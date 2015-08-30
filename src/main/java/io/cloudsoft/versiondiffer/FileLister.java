package io.cloudsoft.versiondiffer;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Finds all files within a given base directory that match a given filter.
 * 
 * Does some initial heavy lifting to allow subsequent efficient queries, such as
 * listing all files or getting all files with a given name.
 */
public class FileLister {
    
    private final Path basedir;
    private final Set<Path> files;
    private final Multimap<String, Path> filenameToPaths;
    
    public FileLister(File basedir) throws IOException {
        this(FileSystems.getDefault().getPath(basedir.getAbsolutePath()), Predicates.alwaysTrue());
    }
    
    public FileLister(final Path basedir, final Predicate<? super String> accepts) throws IOException {
        this.basedir = basedir;
        this.files = Sets.newLinkedHashSet();
        this.filenameToPaths = ArrayListMultimap.create();
        FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (accepts.apply(basedir.relativize(file).toString())) {
                    Path relativePath = basedir.relativize(file);
                    files.add(relativePath);
                    filenameToPaths.put(file.getFileName().toString(), relativePath);
                }
                return FileVisitResult.CONTINUE;
            }};
            
        Files.walkFileTree(basedir, visitor);
    }
    
    public Path getBasedir() {
        return basedir;
    }

    /**
     * Gets all files within the base directory, including recursive sub-directories.
     */
    public Set<Path> listAll() {
        return Collections.unmodifiableSet(files);
    }

    /**
     * Returns the relative paths (compared to {@link #getBasedir()}) of all files
     * with the given filename.
     */
    public Collection<Path> findFilename(String filename) {
        return filenameToPaths.get(filename);
    }

    /**
     * Given a relative path (compared to {@link #getBasedir()}), attempts to get the
     * absolute path (or returns {@link Optional#absent()} if not found.
     */
    public Optional<Path> tryToAbsolute(Path relativePath) {
        return files.contains(relativePath) ? Optional.of(toAbsolute(relativePath)) : Optional.<Path>absent(); 
    }
    
    /**
     * Given a relative path (compared to {@link #getBasedir()}), returns the absolute path.
     * 
     * @throws IllegalArgumentException if the file does not exist. 
     */
    public Path toAbsolute(Path relativePath) {
        Path result = basedir.resolve(relativePath);
        if (!Files.exists(result)) {
            throw new IllegalArgumentException("No such file "+result.toString());
        }
        return result;
    }

    /**
     * Whether the given relative path exists within this {@link #getBasedir()}).
     */
    public boolean contains(Path relativePath) {
        return files.contains(relativePath);
    }
}
