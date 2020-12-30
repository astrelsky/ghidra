/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package agent.gdb.model.impl;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.exception.ExceptionUtils;

import agent.gdb.manager.*;
import agent.gdb.manager.impl.cmd.GdbCommandError;
import ghidra.async.AsyncUtils;
import ghidra.dbg.DebuggerModelClosedReason;
import ghidra.dbg.agent.AbstractDebuggerObjectModel;
import ghidra.dbg.error.DebuggerUserException;
import ghidra.dbg.target.TargetAccessConditioned.TargetAccessibility;
import ghidra.dbg.target.TargetObject;
import ghidra.program.model.address.*;

public class GdbModelImpl extends AbstractDebuggerObjectModel {
	// TODO: Need some minimal memory modeling per architecture on the model/agent side.
	// The model must convert to and from Ghidra's address space names
	protected static final String SPACE_NAME = "ram";

	protected static <T> T translateEx(Throwable ex) {
		Throwable t = AsyncUtils.unwrapThrowable(ex);
		if (t instanceof GdbCommandError) {
			GdbCommandError err = (GdbCommandError) t;
			throw new DebuggerUserException(err.getInfo().getString("msg"));
		}
		return ExceptionUtils.rethrow(ex);
	}

	// Don't make this static, so each model has a unique "GDB" space
	protected final AddressSpace space =
		new GenericAddressSpace(SPACE_NAME, 64, AddressSpace.TYPE_RAM, 0);
	protected final AddressFactory addressFactory =
		new DefaultAddressFactory(new AddressSpace[] { space });

	protected final GdbManager gdb;
	protected boolean noStarti = false;
	protected final GdbModelTargetSession session;

	protected final CompletableFuture<GdbModelTargetSession> completedSession;
	protected final GdbStateListener gdbExitListener = this::checkExited;

	public GdbModelImpl() {
		this.gdb = GdbManager.newInstance();
		this.session = new GdbModelTargetSession(this);

		this.completedSession = CompletableFuture.completedFuture(session);

		gdb.addStateListener(gdbExitListener);
	}

	@Override
	public AddressSpace getAddressSpace(String name) {
		if (!SPACE_NAME.equals(name)) {
			return null;
		}
		return space;
	}

	// TODO: Place make this a model method?
	@Override
	public AddressFactory getAddressFactory() {
		return addressFactory;
	}

	protected void checkExited(GdbState state, GdbCause cause) {
		switch (state) {
			case NOT_STARTED: {
				break;
			}
			case STARTING: {
				break;
			}
			case RUNNING: {
				session.invalidateMemoryAndRegisterCaches();
				session.setAccessibility(TargetAccessibility.INACCESSIBLE);
				break;
			}
			case STOPPED: {
				session.setAccessibility(TargetAccessibility.ACCESSIBLE);
				break;
			}
			case EXIT: {
				try {
					terminate();
				}
				catch (IOException e) {
					throw new AssertionError(e);
				}
				break;
			}
		}
	}

	public CompletableFuture<Void> startGDB(String gdbCmd, String[] args) {
		try {
			gdb.start(gdbCmd, args);
			return gdb.runRC();
		}
		catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	public void consoleLoop() throws IOException {
		gdb.consoleLoop();
	}

	public void terminate() throws IOException {
		listeners.fire.modelClosed(DebuggerModelClosedReason.NORMAL);
		session.invalidateSubtree("GDB is terminating");
		gdb.terminate();
	}

	@Override
	public CompletableFuture<? extends TargetObject> fetchModelRoot() {
		return completedSession;
	}

	@Override
	public boolean isAlive() {
		return gdb.getState().isAlive();
	}

	@Override
	public CompletableFuture<Void> close() {
		try {
			terminate();
			return AsyncUtils.NIL;
		}
		catch (Throwable t) {
			return CompletableFuture.failedFuture(t);
		}
	}
}
