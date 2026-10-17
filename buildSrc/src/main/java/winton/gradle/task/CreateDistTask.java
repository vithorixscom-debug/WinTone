/*
 * This file is part of Winton, a modified version of Baritone.
 * Copyright (c) Baritone contributors.
 * Copyright (c) WinTone contributors, for modifications.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Winton is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Winton.  If not, see <https://www.gnu.org/licenses/>.
 */

package winton.gradle.task;

import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Brady
 * @since 10/12/2018
 */
public class CreateDistTask extends WintonGradleTask {

    private static MessageDigest SHA1_DIGEST;

    @TaskAction
    protected void exec() throws Exception {
        super.doFirst();
        super.verifyArtifacts();

        // Define the distribution file paths
        Path api = getRootRelativeFile("dist/" + getFileName(artifactApiPath));
        Path standalone = getRootRelativeFile("dist/" + getFileName(artifactStandalonePath));
        Path unoptimized = getRootRelativeFile("dist/" + getFileName(artifactUnoptimizedPath));
        Path modrinth = getRootRelativeFile("dist/" + getFileName(artifactModrinthPath));

        // NIO will not automatically create directories
        Path dir = getRootRelativeFile("dist/");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }

        Files.copy(this.artifactApiPath, api, REPLACE_EXISTING);
        Files.copy(this.artifactStandalonePath, standalone, REPLACE_EXISTING);
        Files.copy(this.artifactUnoptimizedPath, unoptimized, REPLACE_EXISTING);
        Files.copy(this.artifactModrinthPath, modrinth, REPLACE_EXISTING);

        // Convenience release name for Modrinth / players
        Path releaseDir = getRootRelativeFile("release/");
        if (!Files.exists(releaseDir)) {
            Files.createDirectory(releaseDir);
        }
        String mcHint = "1.21.11";
        Path releaseJar = releaseDir.resolve(
            "winton-modrinth-fabric-" + mcHint + "-" + getProject().getVersion() + ".jar");
        Files.copy(this.artifactModrinthPath, releaseJar, REPLACE_EXISTING);

        // Calculate all checksums and format them like "shasum"
        List<String> shasum = Files.list(getRootRelativeFile("dist/"))
                .filter(e -> e.getFileName().toString().endsWith(".jar"))
                .map(path -> sha1(path) + "  " + path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        shasum.forEach(System.out::println);

        // Write the checksums to a file
        Files.write(getRootRelativeFile("dist/checksums.txt"), shasum);
    }

    private static String getFileName(Path p) {
        return p.getFileName().toString();
    }

    private static synchronized String sha1(Path path) {
        try {
            if (SHA1_DIGEST == null) {
                SHA1_DIGEST = MessageDigest.getInstance("SHA-1");
            }
            return bytesToHex(SHA1_DIGEST.digest(Files.readAllBytes(path))).toLowerCase();
        } catch (Exception e) {
            // haha no thanks
            throw new IllegalStateException(e);
        }
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
