/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
 * ---
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
 * ---license-end
 */

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

final class FileUtils {
    private FileUtils() {}

    static final Path CWD = Paths.get(System.getProperty("user.dir"));

    /**
     * A file is hidden when its name starts with a dot, on every operating system, or when the operating system says
     * so (hidden attribute on Windows).
     */
    public static boolean isHidden(File file) {
        String name = file.getName();
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        return isDotName(name) || file.isHidden();
    }

    /**
     * Whether a directory above the file, up to the working directory when the file is below it, has a name starting
     * with a dot. The hidden attribute of the operating system is not used for directories: on Windows, the root of a
     * drive carries it, and would hide every file below it.
     */
    public static boolean hasHiddenAncestorDirectory(File file) {
        File absoluteFile;
        try {
            absoluteFile =
                    file.getAbsoluteFile().toPath().toAbsolutePath().normalize().toFile();
        } catch (NullPointerException ignored) {
            return false;
        }

        File ancestor = relativize(absoluteFile).getParentFile();
        while (ancestor != null) {
            if (isDotName(ancestor.getName())) {
                return true;
            }
            ancestor = ancestor.getParentFile();
        }
        return false;
    }

    private static boolean isDotName(String name) {
        return name.startsWith(".") && !name.equals(".") && !name.equals("..");
    }

    public static File relativize(File file) {
        Path absolutePath = file.toPath();
        if (absolutePath.startsWith(CWD)) {
            return CWD.relativize(absolutePath).toFile();
        }
        return absolutePath.toFile();
    }
}
