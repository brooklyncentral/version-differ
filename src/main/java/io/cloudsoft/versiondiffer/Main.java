package io.cloudsoft.versiondiffer;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;

import brooklyn.util.collections.MutableList;
import brooklyn.util.text.StringPredicates;
import io.cloudsoft.versiondiffer.Differ.DifferResult;
import io.cloudsoft.versiondiffer.Differ.DifferResult.AmbiguousResult;
import io.cloudsoft.versiondiffer.Differ.DifferResult.MovedResult;
import io.cloudsoft.versiondiffer.Differ.DifferResult.UnchangedResult;

/**
 * Compares the Java/Groovy source of Brooklyn, given two versions of the source tree.
 * Writes out files that have changed, including what classes have been moved and what is
 * ambiguous.
 */
public class Main {

    // TODO Use a proper CLI parser

    private static final String FQN_REGEX = ".*src.main.java(.*)\\.(java|groovy)";
    private static final Pattern FQN_PATTERN = Pattern.compile(FQN_REGEX);

    public static void main(String[] args) throws IOException {
        String pre = "/Users/aled/repos/cloudsoft/staging-brooklyn";
        String post = "/Users/aled/repos/apache/incubator-brooklyn";
        @SuppressWarnings("unchecked")
        Predicate<String> filter = Predicates.and(
                Predicates.not(StringPredicates.startsWith("examples/")), 
                Predicates.not(StringPredicates.startsWith("sandbox/")), 
                Predicates.not(StringPredicates.startsWith("usage/qa/")), 
                Predicates.not(StringPredicates.containsRegex("src.test.dependencies")),
                Predicates.not(StringPredicates.containsRegex("src.test.resources")),
                StringPredicates.matchesRegex(FQN_REGEX));
        
        Main main = new Main(pre, post, filter);
        DifferResult diff = main.diff();
        main.printSizes(diff);
        main.printFullResult(diff);
        main.printRenames(diff);
    }

    private final String pre;
    private final String post;
    private final Predicate<? super String> filter;

    public Main(String pre, String post, Predicate<String> filter) {
        this.pre = pre;
        this.post = post;
        this.filter = filter;
    }

    protected DifferResult diff() throws IOException {
        FileLister preLister = new FileLister(FileSystems.getDefault().getPath(pre), filter);
        FileLister postLister = new FileLister(FileSystems.getDefault().getPath(post), filter);
        
        return new Differ().compare(preLister, postLister, new JavaFileComparer.Factory());
    }
    
    protected String toFqn(Path path) {
        Matcher matcher = FQN_PATTERN.matcher(path.toString());
        if (!matcher.matches()) {
            throw new IllegalStateException("Path "+path+" does not match regex "+FQN_REGEX);
        }
        String subpath = matcher.group(1);
        subpath = subpath.replaceAll(File.separator, ".");
        if (subpath.startsWith(".")) subpath = subpath.substring(1, subpath.length());
        if (subpath.endsWith(".")) subpath = subpath.substring(0, subpath.length()-1);
        return subpath;
    }
    
    protected void printSizes(DifferResult diff) {
        System.out.println("Unchanged files: "+diff.unchanged.size());
        System.out.println("Moved files    : "+diff.moved.size());
        System.out.println("Ambiguous files: "+diff.ambiguous.size());
        System.out.println("New files      : "+diff.newFiles.size());
    }
    
    protected void printFullResult(DifferResult diff) {
        System.out.println("diff:");
        
        System.out.println("  unchanged:");
        for (UnchangedResult unchanged : sortUnchanged(diff.unchanged())) {
            System.out.println("  - fqn:        "+toFqn(unchanged.path));
            System.out.println("    path:       "+unchanged.path);
            System.out.println("    similarity: "+unchanged.similarity);
        }
        
        System.out.println("  moved:");
        for (MovedResult moved : sortMoved(diff.moved())) {
            System.out.println("  - origFqn:  "+toFqn(moved.origPath));
            System.out.println("    newFqn:   "+toFqn(moved.newPath));
            System.out.println("    origPath: "+moved.origPath);
            System.out.println("    newPath:  "+moved.newPath);
            System.out.println("    similarity: "+moved.similarity);
        }
        
        System.out.println("  ambiguous:");
        for (AmbiguousResult ambiguous : sortAmbiguous(diff.ambiguous())) {
            System.out.println("  - origFqn:  "+toFqn(ambiguous.origPath));
            System.out.println("    origPath: "+ambiguous.origPath);
            System.out.println("    msg:      "+ambiguous.msg);
            if (ambiguous.newPaths.isEmpty()) {
                System.out.println("    newPaths: []");
            } else {
                System.out.println("    newPaths:");
                for (Map.Entry<Path, Double> entry : ambiguous.newPaths.entrySet()) {
                    System.out.println("    - path:       "+entry.getKey());
                    System.out.println("      fqn:        "+toFqn(entry.getKey()));
                    System.out.println("      similarity: "+entry.getValue());
                }
            }
        }
        
        System.out.println("  newFiles:");
        for (Path newFile : sortPaths(diff.newFiles())) {
            System.out.println("  - fqn:  "+toFqn(newFile));
            System.out.println("    path:   "+newFile);
        }
    }
    
    protected void printRenames(DifferResult diff) {
        System.out.println();
        System.out.println("Moved files:");
        for (MovedResult moved : sortMoved(diff.moved())) {
            String origFqn = toFqn(moved.origPath);
            String newFqn = toFqn(moved.newPath);
            System.out.println(Strings.padEnd(origFqn, 80, ' ')+" : " + newFqn);
        }

        System.out.println();
        System.out.println("Ambiguous moved files:");
        for (AmbiguousResult ambiguous : sortAmbiguous(diff.ambiguous())) {
            String origFqn = toFqn(ambiguous.origPath);
            Map.Entry<Path, Double> best = null;
            int numClose = 0;
            for (Map.Entry<Path, Double> entry : ambiguous.newPaths.entrySet()) {
                if (entry.getValue() > 0.75) numClose++;
                if (best == null || best.getValue() < entry.getValue()) {
                    best = entry;
                }
            }
            if (numClose == 1) {
                System.out.println(Strings.padEnd(origFqn, 80, ' ')+" : " + toFqn(best.getKey()));
            } else {
                System.out.println(Strings.padEnd(origFqn, 80, ' ')+" : ????????????????");
            }
        }
    }
    
    protected List<UnchangedResult> sortUnchanged(List<UnchangedResult> orig) {
        MutableList<UnchangedResult> result = MutableList.copyOf(orig);
        Collections.sort(result, new Comparator<UnchangedResult>() {
            @Override public int compare(UnchangedResult o1, UnchangedResult o2) {
                return o1.path.compareTo(o2.path);
            }});
        return result;
    }

    protected List<MovedResult> sortMoved(List<MovedResult> orig) {
        MutableList<MovedResult> result = MutableList.copyOf(orig);
        Collections.sort(result, new Comparator<MovedResult>() {
            @Override public int compare(MovedResult o1, MovedResult o2) {
                return o1.origPath.compareTo(o2.origPath);
            }});
        return result;
    }

    protected List<AmbiguousResult> sortAmbiguous(List<AmbiguousResult> orig) {
        MutableList<AmbiguousResult> result = MutableList.copyOf(orig);
        Collections.sort(result, new Comparator<AmbiguousResult>() {
            @Override public int compare(AmbiguousResult o1, AmbiguousResult o2) {
                return o1.origPath.compareTo(o2.origPath);
            }});
        return result;
    }

    protected List<Path> sortPaths(List<Path> orig) {
        MutableList<Path> result = MutableList.copyOf(orig);
        Collections.sort(result);
        return result;
    }
}
