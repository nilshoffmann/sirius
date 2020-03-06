package de.unijena.bioinf.ms.frontend.subtools.sirius;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.jobs.SiriusJobs;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.ft.model.Whiteset;
import de.unijena.bioinf.ChemistryBase.ms.properties.FinalConfig;
import de.unijena.bioinf.chemdb.annotations.FormulaSearchDB;
import de.unijena.bioinf.fingerid.FormulaWhiteListJob;
import de.unijena.bioinf.ms.frontend.core.ApplicationCore;
import de.unijena.bioinf.ms.frontend.io.projectspace.Instance;
import de.unijena.bioinf.ms.frontend.subtools.InstanceJob;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import de.unijena.bioinf.projectspace.sirius.FormulaResultRankingScore;
import de.unijena.bioinf.sirius.IdentificationResult;
import de.unijena.bioinf.sirius.Sirius;
import de.unijena.bioinf.sirius.scores.SiriusScore;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class SiriusSubToolJob extends InstanceJob {
    protected final SiriusOptions cliOptions;

    public SiriusSubToolJob(SiriusOptions cliOptions) {
        this.cliOptions = cliOptions;
    }


    @Override
    protected void computeAndAnnotateResult(final @NotNull Instance inst) throws Exception {
        final Ms2Experiment exp = inst.getExperiment();
        final CompoundContainer ioC = inst.loadCompoundContainer();
//        System.out.println(new Date() + "\t-> I am Sirius, start computing Experiment " + inst.getID());


        if (ioC.getResults().isEmpty() || isRecompute(inst)) {
            invalidateResults(inst);

            // set whiteSet or merge with whiteSet from db search if available
            Whiteset wSet = null;

            // create WhiteSet from DB if necessary
            final Optional<FormulaSearchDB> searchDB = exp.getAnnotation(FormulaSearchDB.class);
            if (searchDB.isPresent() && searchDB.get().hasSearchableDB()) {
                FormulaWhiteListJob wsJob = new FormulaWhiteListJob(ApplicationCore.WEB_API, searchDB.get().value, exp);
                wSet = SiriusJobs.getGlobalJobManager().submitJob(wsJob).awaitResult();
            }

            // todo this should be moved to annotations at some point.
            // so that the cli parser dependency can be removed
            if (cliOptions.formulaWhiteSet != null) {
                if (wSet != null)
                    wSet = wSet.union(cliOptions.formulaWhiteSet);
                else
                    wSet = cliOptions.formulaWhiteSet;
            }

            exp.setAnnotation(Whiteset.class, wSet);

            final Sirius sirius = ApplicationCore.SIRIUS_PROVIDER.sirius(exp.getAnnotationOrThrow(FinalConfig.class).config.getConfigValue("AlgorithmProfile"));
            List<IdentificationResult<SiriusScore>> results = SiriusJobs.getGlobalJobManager().submitJob(sirius.makeIdentificationJob(exp)).awaitResult();

            //write results to project space
            for (IdentificationResult<SiriusScore> result : results)
                inst.newFormulaResultWithUniqueId(result.getTree());

            // set sirius to ranking score
            if (exp.getAnnotation(FormulaResultRankingScore.class).orElse(FormulaResultRankingScore.AUTO).isAuto()) {
                inst.getID().setRankingScoreTypes(new ArrayList<>(List.of(SiriusScore.class)));
                inst.updateCompoundID();
            }


            /*String out = "#####################################\n"
                    + new Date() + "\t-> I am Sirius, finish with Experiment " + inst.getID() + "\n"
                    + results.stream().map(id -> id.getMolecularFormula().toString() + " : " + id.getScore()).collect(Collectors.joining("\n"))
                    + "\n#####################################";

            System.out.println(out);*/

        } else {
            logInfo("Skipping formula Identification for Instance \"" + exp.getName() + "\" because results already exist.");
//            System.out.println("Skipping formula Identification for Instance \"" + exp.getName() + "\" because results already exist.");
        }
    }
}