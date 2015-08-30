package io.cloudsoft.versiondiffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.StringPredicates;

/**
 * Compares the similarity of Java source files. Ignores things like
 * imports and license header (which are usually just noise).
 */
public class JavaFileComparer implements FileComparer {

    public static class Factory implements FileComparer.Factory {
        public FileComparer newComparer(Path p1) throws IOException {
            return new JavaFileComparer(p1);
        }
    }
    
    private static final List<String> LICENSE_HEADER = ImmutableList.of(
            "/*",
            " * Licensed to the Apache Software Foundation (ASF) under one",
            " * or more contributor license agreements.  See the NOTICE file",
            " * distributed with this work for additional information",
            " * regarding copyright ownership.  The ASF licenses this file",
            " * to you under the Apache License, Version 2.0 (the",
            " * \"License\"); you may not use this file except in compliance",
            " * with the License.  You may obtain a copy of the License at",
            " *",
            " *     http://www.apache.org/licenses/LICENSE-2.0",
            " *",
            " * Unless required by applicable law or agreed to in writing,",
            " * software distributed under the License is distributed on an",
            " * \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY",
            " * KIND, either express or implied.  See the License for the",
            " * specific language governing permissions and limitations",
            " * under the License.",
            " */");
    
    private static final String LICENSE_HEADER_TRIMMED = trimAndJoinLines(LICENSE_HEADER);

    private final Path p1;
    private final String s1;
    
    public JavaFileComparer(Path p1) throws IOException {
        this.p1 = p1;
        this.s1 = strippedJavaSource(p1);
    }
    
    public boolean isJavaSourceFile() {
        return p1.getFileName().toString().endsWith(".java");
    }
    
    /**
     * Compares the Java source of the files. Ignores things like
     * imports and license header that are just noise.
     */
    @Override
    public double similarity(Path p2) throws IOException {
        String s2 = strippedJavaSource(p2);
        
        // Extremely slow!
        // int threshold = (int) (0.25 * s1.length());
        // int result = StringUtils.getLevenshteinDistance(s1, s2, threshold);
        // return result >= 0;
        
        double similarity = LetterPairSimilarity.compareStrings(s1, s2);
        return similarity;
    }
    
    private String strippedJavaSource(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (startsWithLicenseHeader(lines)) {
            lines = lines.subList(LICENSE_HEADER.size(), lines.size());
        }
        return Joiner.on("\n").join(FluentIterable.from(lines).filter(Predicates.not(StringPredicates.startsWith("import "))));
    }
    
    private boolean startsWithLicenseHeader(List<String> lines) {
        if (lines.size() < LICENSE_HEADER.size()) {
            return false;
        }
        String start = trimAndJoinLines(lines.subList(0, LICENSE_HEADER.size()));
        return start.equals(LICENSE_HEADER_TRIMMED);
    }

    private static String trimAndJoinLines(List<String> lines) {
        return Joiner.on("\n").join(Lists.transform(lines, StringFunctions.trim()));
    }
}
