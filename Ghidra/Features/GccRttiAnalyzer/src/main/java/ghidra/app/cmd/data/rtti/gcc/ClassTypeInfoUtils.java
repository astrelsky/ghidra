package ghidra.app.cmd.data.rtti.gcc;

import static ghidra.app.cmd.data.rtti.gcc.GccUtils.PURE_VIRTUAL_FUNCTION_NAME;

import java.util.*;

import ghidra.app.cmd.data.rtti.ClassTypeInfo;
import ghidra.app.cmd.data.rtti.TypeInfo;
import ghidra.app.cmd.data.rtti.Vtable;
import ghidra.app.cmd.data.rtti.gcc.factory.TypeInfoFactory;
import ghidra.app.util.XReferenceUtil;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.DumbMemBufferImpl;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.scalar.Scalar;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolTable;
import ghidra.util.Msg;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

public class ClassTypeInfoUtils {

	private ClassTypeInfoUtils() {
	}

	/**
	 * Finds the Vtable for the corresponding TypeInfo.
	 * 
	 * @param program the program to be searched.
	 * @param address the address of the TypeInfo Model's DataType.
	 * @param monitor the taskmonitor to be used while searching for the vtable.
	 * @return The TypeInfo's Vtable Model or null if none exists.
	 */
	public static Vtable findVtable(Program program, Address address, TaskMonitor monitor)
		throws CancelledException, InvalidDataTypeException {
			SymbolTable table = program.getSymbolTable();
			Listing listing = program.getListing();
			TypeInfo typeinfo = TypeInfoFactory.getTypeInfo(program, address);
			typeinfo.validate();
			if (!(typeinfo instanceof ClassTypeInfo)) {
				throw new InvalidDataTypeException(
					"Invalid ClassTypeInfo at "+address.toString());
			}
			ClassTypeInfo type = (ClassTypeInfo) typeinfo;
			for (Symbol symbol : table.getChildren(typeinfo.getNamespace().getSymbol())) {
				if (symbol.getName().equals(VtableModel.SYMBOL_NAME)) {
					VtableModel vtable = new VtableModel(program, symbol.getAddress());
					try {
						vtable.validate();
						return vtable;
					} catch (InvalidDataTypeException e) {
						break;
					}
				}
			}
			Set<Address> references = Collections.emptySet();
			Data tiData = listing.getDataAt(address);
			if (tiData != null) {
				List<Address> referenceList = Arrays.asList(XReferenceUtil.getXRefList(tiData));
				references = GccUtils.getDirectDataReferences(program, address);
				references.removeAll(referenceList);
			}
			if (references.isEmpty()) {
				references = GccUtils.getDirectDataReferences(program, address);
			}
			return getValidVtable(program, references, monitor, type);
	}

	private static boolean invalidData(Data data) {
		if (data == null) {
			return false;
		}
		if (data.getDataType() instanceof Pointer) {
			return false;
		}
		if (data.getDataType() instanceof DefaultDataType) {
			return false;
		}
		return true;
	}

	private static Vtable getValidVtable(Program program, Set<Address> references,
		TaskMonitor monitor, ClassTypeInfo typeinfo) throws CancelledException {
			Listing listing = program.getListing();
			Memory mem = program.getMemory();
			DataType ptrDiff = GccUtils.getPtrDiff_t(program.getDataTypeManager());
			Scalar zero = new Scalar(ptrDiff.getLength(), 0);
			boolean hasPureVirtual = program.getSymbolTable().getSymbols(
				PURE_VIRTUAL_FUNCTION_NAME).hasNext();
			for (Address reference : references) {
				monitor.checkCanceled();
				MemBuffer buf = new DumbMemBufferImpl(
					mem, reference.subtract(ptrDiff.getLength()));
				Object value = ptrDiff.getValue(
					buf, ptrDiff.getDefaultSettings(), ptrDiff.getLength());
				if(!zero.equals(value)) {
					continue;
				}
				Data data = listing.getDataContaining(reference);
				if (invalidData(data)) {
					continue;
				}
				VtableModel vtable = new VtableModel(program, reference, typeinfo);
				try {
					Function[][] functionTables = vtable.getFunctionTables();
					if (functionTables.length > 0) {
						if (functionTables[0].length > 0) {
							if (functionTables[0][0] == null) {
								for (Function function : functionTables[0]) {
									if (function == null) {
										continue;
									} if (hasPureVirtual) {
										if (PURE_VIRTUAL_FUNCTION_NAME.equals(function.getName())){
											return vtable;
										}
									} else {
										return vtable;
									}
								}
								// construction vtable
								continue;
							}
						}
					}
				} catch (InvalidDataTypeException e) {
					continue;
				}
				return vtable;
			}
			try {
				Msg.warn(ClassTypeInfoUtils.class,
						  "Unable to find vtable for "+typeinfo.getNamespace().getName(true));
			} catch (InvalidDataTypeException e) {
				Msg.error(ClassTypeInfoUtils.class, e);
			}
			return VtableModel.NO_VTABLE;
	}

	/**
	 * Sorts a list of classes in order of most derived.
	 * 
	 * @param program
	 * @param classes
	 * @throws InvalidDataTypeException if the list contains an invalid ClassTypeInfo
	 */
	public static void sortByMostDerived(Program program, List<ClassTypeInfo> classes)
		throws InvalidDataTypeException {
			Set<ClassTypeInfo> classSet = new LinkedHashSet<>(classes);
			List<ClassTypeInfo> sortedClasses = new ArrayList<>(classes.size());
			Iterator<ClassTypeInfo> classIterator = classSet.iterator();
			while (classIterator.hasNext()) {
				ClassTypeInfo type = classIterator.next();
				ArrayDeque<ClassTypeInfo> stack = new ArrayDeque<>();
				stack.push(type);
				while(!stack.isEmpty()) {
					ClassTypeInfo classType = stack.pop();
					if (classType.hasParent() && classSet.contains(classType)) {
						ClassTypeInfo parent = classType.getParentModels()[0];
						if (classSet.contains(parent)) {
							stack.push(classType);
							stack.push(parent);
							continue;
						}
					}
					sortedClasses.add(classType);
					classSet.remove(classType);
				} classIterator = classSet.iterator();
			}
			classes.clear();
			classes.addAll(sortedClasses);
	}

}