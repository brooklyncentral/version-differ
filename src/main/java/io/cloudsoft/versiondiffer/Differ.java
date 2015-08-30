package io.cloudsoft.versiondiffer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import brooklyn.util.collections.MutableSet;
import io.cloudsoft.versiondiffer.Differ.DifferResult.AmbiguousResult;
import io.cloudsoft.versiondiffer.Differ.DifferResult.MovedResult;
import io.cloudsoft.versiondiffer.Differ.DifferResult.UnchangedResult;

public class Differ {

    private static final Logger LOGGER = Logger.getLogger(Differ.class.getName());
        
    public static class DifferResult {
        public static class UnchangedResult {
            public final Path path;
            public final double similarity;
            
            public UnchangedResult(Path path, double similarity) {
                this.path = path;
                this.similarity = similarity;
            }
        }

        public static class MovedResult {
            public final Path origPath;
            public final Path newPath;
            public final double similarity;
            
            public MovedResult(Path origPath, Path newPath, double similarity) {
                this.origPath = origPath;
                this.newPath = newPath;
                this.similarity = similarity;
            }
        }

        public static class AmbiguousResult {
            public final Path origPath;
            public final Map<Path, Double> newPaths;
            public final String msg;
            
            public AmbiguousResult(Path origPath, Map<Path, Double> newPaths, String msg) {
                this.origPath = origPath;
                this.newPaths = ImmutableMap.copyOf(newPaths);
                this.msg = msg;
            }
        }

        final List<UnchangedResult> unchanged = Lists.newArrayList();
        final List<MovedResult> moved = Lists.newArrayList();
        final List<AmbiguousResult> ambiguous = Lists.newArrayList();
        final List<Path> newFiles = Lists.newArrayList();
        
        public List<UnchangedResult> unchanged() {
            return Collections.unmodifiableList(unchanged);
        }
        public List<MovedResult> moved() {
            return Collections.unmodifiableList(moved);
        }
        public List<AmbiguousResult> ambiguous() {
            return Collections.unmodifiableList(ambiguous);
        }
        public List<Path> newFiles() {
            return Collections.unmodifiableList(newFiles);
        }
    }
    
    public DifferResult compare(FileLister preLister, FileLister postLister, FileComparer.Factory fileComparerFactory) throws IOException {
        LOGGER.info("Comparing "+preLister.getBasedir()+" and "+postLister.getBasedir());
        
        DifferResult result = new DifferResult();
        for (Path path : preLister.listAll()) {
            LOGGER.info("  Comparing "+path);
            
            FileComparer fileComparer = fileComparerFactory.newComparer(preLister.toAbsolute(path));
            
            // Is there an exact match - i.e. still a file in that location; and is its contents similar
            Optional<Path> val = postLister.tryToAbsolute(path);
            if (val.isPresent()) {
                double similarity = fileComparer.similarity(val.get());
                if (similarity >= 0.75) {
                    result.unchanged.add(new UnchangedResult(path, similarity));
                } else {
                    result.ambiguous.add(new AmbiguousResult(path, ImmutableMap.of(path, similarity), "File significantly changed; is it definitely the same file?"));
                }
                continue;
            }
            
            // Is there a file with the same name?
            Collection<Path> contenders = postLister.findFilename(path.getFileName().toString());
            if (contenders.size() == 1) {
                Path newPath = Iterables.getOnlyElement(contenders);
                Optional<Path> newPathAbsolute = postLister.tryToAbsolute(newPath);
                double similarity = fileComparer.similarity(newPathAbsolute.get());

                if (similarity >= 0.75) {
                    result.moved.add(new MovedResult(path, newPath, similarity));
                } else {
                    result.ambiguous.add(new AmbiguousResult(path, ImmutableMap.of(newPath, similarity), "Moved file significantly changed; is it definitely the same file?"));
                }
                continue;
                
            } else if (contenders.isEmpty()) {
                // Future enhancement: could look for a renamed file.
                result.ambiguous.add(new AmbiguousResult(path, ImmutableMap.<Path, Double>of(), "No file with same name"));
                continue;
                
            } else {
                Map<Path, Double> newPaths = Maps.newLinkedHashMap();
                for (Path newPath : contenders) {
                    Optional<Path> newPathAbsolute = postLister.tryToAbsolute(newPath);
                    double similarity = fileComparer.similarity(newPathAbsolute.get());
                    newPaths.put(newPath, similarity);
                }
                
                result.ambiguous.add(new AmbiguousResult(path, newPaths, "Multiple files with same name as original"));
                continue;
            }
        }
        
        // Is there a file that looks like a rename?
        Set<Path> postFilesUnaccountedFor = MutableSet.copyOf(postLister.listAll());
        for (UnchangedResult unchanged : result.unchanged) {
            postFilesUnaccountedFor.remove(unchanged.path);
        }
        for (MovedResult moved : result.moved) {
            postFilesUnaccountedFor.remove(moved.newPath);
        }
        for (AmbiguousResult ambiguous : result.ambiguous) {
            postFilesUnaccountedFor.removeAll(ambiguous.newPaths.keySet());
        }
        result.newFiles.addAll(postFilesUnaccountedFor);
        
        return result;
    }
}
