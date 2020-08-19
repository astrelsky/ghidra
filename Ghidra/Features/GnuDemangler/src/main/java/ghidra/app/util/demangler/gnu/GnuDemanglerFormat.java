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
package ghidra.app.util.demangler.gnu;

/**
 * Enum representation of the available GnuDemangler formats
 */
public enum GnuDemanglerFormat {
    // OLD: none,auto,gnu,lucid,arm,hp,edg,gnu-v3,java,gnat
    // NEW: none,auto,gnu-v3,java,gnat,dlang,rust
    /** Automatic mangling format detection */
    AUTO("", 0),
    /** GNUv2 mangling format */
    GNU("gnu", -1),
    /** lucid mangling format */
    LUCID("lucid", -1),
    /** arm mangling format */
    ARM("arm", -1),
    /** hp mangling format */
    HP("hp", -1),
    /** mangling format used by the Edison Design Group (EDG) compiler */
    EDG("edg", -1),
    /** GNUv3 mangling format */
    GNUV3("gnu-v3", 0),
    /** Java mangling format */
    JAVA("java", 0),
    /** GNAT Ada compiler mangling format */
    GNAT("gnat", 0),
    /** D mangling format */
    DLANG("dlang", 1),
    /** Rust mangling format */
    RUST("rust", 1);

    /** the format option string */
    private final String format;
    /** private sentinal. deprecated = -1, both = 0, new = 1 */
    private final byte version;

    private GnuDemanglerFormat(String format, int version) {
        this.format = format;
        this.version = (byte) version;
    }

    /**
     * Checks if this format is available in the deprecated gnu demangler
     * @return true if this format is available in the deprecated gnu demangler
     */
    public boolean isDeprecatedFormat() {
        return version <= 0;
    }

    /**
     * Checks if this format is available in a modern version of the gnu demangler
     * @return true if this format is available in a modern version of the gnu demangler.
     */
    public boolean isModernFormat() {
        return version >= 0;
    }
    
    /**
     * Checks if this format is available for the specified demangler
     * @param isDeprecated true for the deprecated demangler, false for the modern demangler.
     * @return true if the format is available
     */
    public boolean isAvailable(boolean isDeprecated) {
        return isDeprecated ? isDeprecatedFormat() : isModernFormat();
    }

    /**
     * Gets the format option to be passed to the demangler via the <code>-s</code> option
     * @return the format option to be passed to the demangler
     */
    public String getFormat() {
        return format;
    }
}
