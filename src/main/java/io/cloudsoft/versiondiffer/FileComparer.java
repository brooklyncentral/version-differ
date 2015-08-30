package io.cloudsoft.versiondiffer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Compares the similarity of pairs of files: 1.0 means identical; 0.0 means nothing in common.
 * 
 * Constructed with the first file, which can then be compared against other files.
 */
public interface FileComparer {

    /**
     * For creating a {@link FileComparer}.
     */
    public static interface Factory {
        public FileComparer newComparer(Path p1) throws IOException;
    }
    
    /**
     * The level of similarity between two files: 1.0 means identical; 0.0 means nothing in common.
     * Compares the file at the given path with the file used when creating this {@link FileComparer}.
     * See {@link FileComparer.Factory}.
     * 
     * @param p2
     * @return
     * @throws IOException
     */
    public double similarity(Path p2) throws IOException;
}
