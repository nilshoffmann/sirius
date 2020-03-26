package de.unijena.bioinf.ms.frontend.subtools.projectspace;

import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ms.frontend.subtools.Provide;
import de.unijena.bioinf.ms.frontend.subtools.RootOptions;
import de.unijena.bioinf.ms.frontend.subtools.StandaloneTool;
import de.unijena.bioinf.ms.properties.ParameterConfig;
import de.unijena.bioinf.projectspace.CompoundContainerId;
import picocli.CommandLine;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@CommandLine.Command(name = "project-space", aliases = {"PS"}, description = "<STANDALONE> Modify a given project Space: Read project(s) with --input, apply modification and write the result via --output. If either onl --input or --output is give the modifications will be made in-place.",  versionProvider = Provide.Versions.class, mixinStandardHelpOptions = true, showDefaultValues = true)
public class ProjecSpaceOptions implements StandaloneTool<ProjectSpaceWorkflow> {


    @CommandLine.Option(names = {"--delete-by-idx", "--di", "-d"}, split = ",",
            description = {"Delete all compounds that match the given indices from the given project-space."})
    private void makeDeleteIdxFilter(Set<Integer> idxs) {
        deleteIdxFilter = (c) -> !idxs.contains(c.getCompoundIndex());
    }

    Predicate<CompoundContainerId> deleteIdxFilter = (c) -> true;


    @CommandLine.Option(names = {"--delete-by-mass", "--dm"},
            description = {"Delete all compounds that are within the given mass window."
                    ,"Example: Use 'mass1:mass2' to match compounds with mass1 <= mass <= mass2. Leave a value empty to set no bound."})
    private void makeDeleteMassFilter(String mass) {
        String[] masses = mass.split(":");
        final double gt = masses[0].strip().isBlank() ? 0d : Double.parseDouble(masses[0]);
        final double lt = masses.length < 2 || masses[1].strip().isBlank() ? Double.POSITIVE_INFINITY : Double.parseDouble(masses[1]);
        deleteIdxFilter = (c) -> !(c.getIonMass().map(m -> gt <= m && m <= lt).orElse(false));
        deleteMassFilterExp = (c) -> !(gt <= c.getIonMass() && c.getIonMass() <= lt);
    }

    Predicate<CompoundContainerId> deleteMassFilter = (c) -> true;
    Predicate<Ms2Experiment> deleteMassFilterExp = (c) -> true;


    @CommandLine.Option(names = {"--delete-by-name", "--dn"},
            description = "Delete all compounds where the 'identifier' (dir name, ID) matches the given regex (JAVA).")
    private void makeDeleteIdxFilter(String regex) {
        final Pattern m = Pattern.compile(regex);
        deleteIdxFilter = (c) -> !m.matcher(c.getDirectoryName()).find();
    }

    Predicate<CompoundContainerId> deleteNameFilter = (c) -> true;


    @CommandLine.Option(names = {"--keep-by-idx", "--ki", "-k"}, split = ",",
            description = {"Keep all compounds that match the given indices from the given project-space"})
    private void makeKeepIdxFilter(Set<Integer> idxs) {
        keepIdxFilter = (c)-> idxs.contains(c.getCompoundIndex());
    }

    Predicate<CompoundContainerId> keepIdxFilter = (c) -> true;


    @CommandLine.Option(names = {"--keep-by-mass", "--km"},
            description = {"Keep all compounds that are within the given mass window."
                    ,"Example: Use 'mass1:mass2' to match compounds with mass1 <= mass <= mass2. Leave a value empty to set no bound."})
    public void makeKeepMassFilter(String mass) {
        String[] masses = mass.split(":");
        final double gt = masses[0].strip().isBlank() ? 0d : Double.parseDouble(masses[0]);
        final double lt = masses.length < 2 || masses[1].strip().isBlank() ? Double.POSITIVE_INFINITY : Double.parseDouble(masses[1]);
        keepMassFilter = (c) -> (c.getIonMass().map(m -> gt <= m && m <= lt).orElse(true));
        keepMassFilterExp = (c) -> (gt <= c.getIonMass() && c.getIonMass() <= lt);
    }

    Predicate<CompoundContainerId> keepMassFilter = (c) -> true;
    Predicate<Ms2Experiment> keepMassFilterExp = (c) -> true;


    @CommandLine.Option(names = {"--keep-by-name", "--kn"},
            description = "Keep all compounds where the 'identifier' (Dir name, ID) matches the given regex (JAVA).")
    private void makeKeepNameFilter(String regex) {
        final Pattern m = Pattern.compile(regex);
        keepNameFilter = (c) -> m.matcher(c.getDirectoryName()).find();
    }

    Predicate<CompoundContainerId> keepNameFilter = (c) -> true;


    public Predicate<CompoundContainerId> getCombinedFilter() {
        return (c) -> deleteIdxFilter.test(c)
                && deleteMassFilter.test(c)
                && deleteNameFilter.test(c)
                && keepIdxFilter.test(c)
                && keepMassFilter.test(c)
                && keepNameFilter.test(c)
                ;
    }

    public Predicate<Ms2Experiment> getCombinedMS2ExpFilter() {
        return (c) -> deleteMassFilterExp.test(c)
                && keepMassFilterExp.test(c)
                ;
    }


    @CommandLine.ArgGroup(exclusive = false, heading = "@|bold Split the project into chunks: %n|@")
    protected SplitProject splitOptions = new SplitProject();

    static class SplitProject {
        enum Order {SHUFFLE, MASS, NAME}
        enum SplitType {NO, NUMBER, SIZE}

        @CommandLine.Option(names = {"--split", "-s"}, defaultValue = "NO", description = "Split the output into batches. Either in a specific number of batches or in batches of specific size!")
        SplitType type;

        @CommandLine.Option(names = {"--count", "-c"}, defaultValue = "1", description = "Sets batch number or batch size depending on <--split>")
        int count = 1;

        @CommandLine.Option(names = {"--split-order", "-o"}, defaultValue = "SHUFFLE", description = "Specify the order of Compounds before putting them into batches.")
        Order order = Order.SHUFFLE;


    }

    @CommandLine.Option(names = {"--move", "-m"}, description = "DANGERZONE: Move instead of copy data (where possible) when merging or splitting projects to save time. Be aware of the risk that you may end up with corrupted input or output data when the program crashes.")
    public boolean move = false;

    public ProjectSpaceWorkflow makeWorkflow(RootOptions<?,?,?> rootOptions, ParameterConfig config) {
        return new ProjectSpaceWorkflow(rootOptions, this, config);
    }
}

