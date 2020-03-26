/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unijena.bioinf.ms.frontend.subtools.sirius;

import de.unijena.bioinf.ChemistryBase.ms.ft.model.Whiteset;
import de.unijena.bioinf.chemdb.DataSource;
import de.unijena.bioinf.ms.frontend.DefaultParameter;
import de.unijena.bioinf.ms.frontend.completion.DataSourceCandidates;
import de.unijena.bioinf.ms.frontend.subtools.InstanceJob;
import de.unijena.bioinf.ms.frontend.subtools.Provide;
import de.unijena.bioinf.ms.frontend.subtools.config.DefaultParameterConfigLoader;
import de.unijena.bioinf.sirius.SiriusCachedFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * This is for SIRIUS specific parameters.
 * <p>
 * They may be annotated to the MS2 Experiment
 *
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */

//todo got descriprions from defaultConfigOptions
@Command(name = "formula", aliases = {"tree","sirius", "F"}, description = "<COMPOUND_TOOL> Identify molecular formula for each compound individually using fragmentation trees and isotope patterns.",  versionProvider = Provide.Versions.class,  mixinStandardHelpOptions = true, sortOptions = false)
public class SiriusOptions implements Callable<InstanceJob.Factory<SiriusSubToolJob>> {
    protected final DefaultParameterConfigLoader defaultConfigOptions;

    public SiriusOptions(DefaultParameterConfigLoader defaultConfigOptions) {
        this.defaultConfigOptions = defaultConfigOptions;
    }

    @Option(names = "--ppm-max", descriptionKey = "MS1MassDeviation.allowedMassDeviation", description = "Maximum allowed mass deviation in ppm for decomposing masses.")
    public void setPpmMax(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("MS1MassDeviation.allowedMassDeviation", value + "ppm");
    }

    @Option(names = "--ppm-max-ms2", descriptionKey = "MS2MassDeviation.allowedMassDeviation", description = "Maximum allowed mass deviation in ppm for decomposing masses in MS2. If not specified, the same value as for the MS1 is used.")
    public void setPpmMaxMs2(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("MS2MassDeviation.allowedMassDeviation", value + "ppm");
    }

    @Option(names = "--tree-timeout", descriptionKey = "Timeout.secondsPerTree", description = "Time out in seconds per fragmentation tree computations. 0 for an infinite amount of time.")
    public void setTreeTimeout(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("Timeout.secondsPerTree", value);
    }

    @Option(names = "--compound-timeout", descriptionKey = "Timeout.secondsPerInstance", description = "Maximal computation time in seconds for a single compound. 0 for an infinite amount of time.")
    public void setInstanceTimeout(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("Timeout.secondsPerInstance", value);
    }

    @Option(names = "--no-recalibration", descriptionKey = "ForbidRecalibration" , description = "Disable Recalibration of input Spectra")
    public void disableRecalibration(DefaultParameter disable) throws Exception {
        if (disable.asBoolean()){
            defaultConfigOptions.changeOption("ForbidRecalibration", "FORBIDDEN");
        }
    }

    @Option(names = {"-p", "--profile"}, descriptionKey ="AlgorithmProfile" , description = {"Name of the configuration profile.", "Predefined profiles are: `default`, 'qtof', 'orbitrap', 'fticr'."})
    public void setProfile(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("AlgorithmProfile", value);
    }

    // candidates
    @Option(names = {"-c", "--candidates"}, descriptionKey ="NumberOfCandidates" , description = "Number of formula candidates in the output.")
    public void setNumberOfCandidates(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("NumberOfCandidates", value);
    }

    @Option(names = "--candidates-per-ion", descriptionKey = "NumberOfCandidatesPerIon", description = "Minimum number of candidates in the output for each ionization. Set to force output of results for each possible ionization, even if not part of highest ranked results.")
    public void setNumberOfCandidatesPerIon(DefaultParameter value) throws Exception {
        defaultConfigOptions.changeOption("NumberOfCandidatesPerIon", value);
    }

    // Elements
    @Option(names = {"-e", "--elements-considered"}, description = {"Set the allowed elements for rare element detection.", "Example: `SBrClBSe` to allow the elements S,Br,Cl,B and Se.","Default: Is autodetected, see 'FormulaSettings.detectable' and 'FormulaSettings.fallback' for details."})
    public void setDetectableElements(List<String> elements) throws Exception {
        defaultConfigOptions.changeOption("FormulaSettings.detectable", elements);
        defaultConfigOptions.changeOption("FormulaSettings.fallback", elements);
    }

    @Option(names = {"-E", "--elements-enforced"}, descriptionKey = "FormulaSettings.enforced", description = {"Enforce elements for molecular formula determination. ", "Example: CHNOPSCl to allow the elements C, H, N, O, P, S and Cl. Add numbers in brackets to restrict the minimal and maximal allowed occurrence of these elements: CHNOP[5]S[8]Cl[1-2]. When one number is given then it is interpreted as upper bound."})
    public void setEnforcedElements(DefaultParameter elements) throws Exception {
        defaultConfigOptions.changeOption("FormulaSettings.enforced", elements);
    }

    @Option(names = {"--database", "-d", "--db"}, descriptionKey = "FormulaSearchDB" , paramLabel = DataSourceCandidates.PATAM_LABEL, completionCandidates = DataSourceCandidates.class,
            description = {"Search formulas in the Union of the given databases. If no database is given all possible molecular formulas will be respected (no database is used).", DataSourceCandidates.VALID_DATA_STRING})
    public void setDatabase(DefaultParameter dbList) throws Exception {
        defaultConfigOptions.changeOption("FormulaSearchDB", dbList);
    }

    @Option(names = {"-f", "--formulas"}, description = "Specify a list of candidate formulas the method should use. Omit this option if you want to consider all possible molecular formulas")
    public void setFormulaWhiteList(List<String> formulaWhiteList) {
        formulaWhiteSet = Whiteset.of(formulaWhiteList);
    }
    public Whiteset formulaWhiteSet =  null;


    @Option(names = {"--no-isotope-filter"}, descriptionKey = "IsotopeSettings.filter", description = "Disable molecular formula filter. When filtering is enabled, molecular formulas are excluded if their theoretical isotope pattern does not match the theoretical one, even if their MS/MS pattern has high score.")
    public void disableIsotopeFilter(DefaultParameter disable) throws Exception {
        defaultConfigOptions.changeOption("IsotopeSettings.filter", disable.invertBool());
    }

    @Option(names = {"--no-isotope-score"}, descriptionKey = "IsotopeSettings.multiplier", description = "Disable isotope pattern score.")
    public void disableIsotopeScore(DefaultParameter disable) throws Exception {
        if (disable.asBoolean())
            defaultConfigOptions.changeOption("IsotopeSettings.multiplier", "0");
    }

    //Adducts
    @Option(names = {"-i", "--ions-considered"}, descriptionKey = "AdductSettings.detectable" , description = "the iontype/adduct of the MS/MS data. Example: [M+H]+, [M-H]-, [M+Cl]-, [M+Na]+, [M]+. You can also provide a comma separated list of adducts.")
    public void setIonsConsidered(DefaultParameter adducts) throws Exception {
        defaultConfigOptions.changeOption("AdductSettings.detectable", adducts);
    }

    @Option(names = {"-I", "--ions-enforced"}, descriptionKey = "AdductSettings.enforced", description = "the iontype/adduct of the MS/MS data. Example: [M+H]+, [M-H]-, [M+Cl]-, [M+Na]+, [M]+. You can also provide a comma separated list of adducts.")
    public void setIonsEnforced(DefaultParameter adducts) throws Exception {
        defaultConfigOptions.changeOption("AdductSettings.enforced", adducts);
    }


    @CommandLine.ArgGroup(exclusive = true)
    private void setElementDetection(ElementDetection ed){
        ed.defaultConfigOptions = defaultConfigOptions;
    }

    private static class ElementDetection {
        private DefaultParameterConfigLoader defaultConfigOptions;

        // some hidden parameters
        @Option(names = "--disable-element-detection", hidden = true)
        public void disableElementDetection(boolean disable) throws Exception {
            if (disable)
                defaultConfigOptions.changeOption("FormulaSettings.detectable", " , ");
        }

        @Option(names = "--enable-silicon-detection", hidden = true)
        public void enableSiliconDetection(boolean enable) throws Exception {
            if (enable) {
                String value = defaultConfigOptions.config.getConfigValue("FormulaSettings.detectable");
                if (value.isEmpty())
                    defaultConfigOptions.changeOption("FormulaSettings.detectable", "Si");
                else if (!value.contains("Si"))
                    defaultConfigOptions.changeOption("FormulaSettings.detectable", value + ",Si");
            }
        }
    }

    @Option(names = "--trust-ion-prediction", description = "By default we use MS1 information to select additional ionizations ([M+Na]+,[M+K]+,[M+Cl]-,[M+Br]-) for considerations. With this parameter we trust the MS1 prediction and only consider these found ionizations.", hidden = true)
    public void setTrustGuessIonFromMS1(boolean trust) {
        throw new IllegalArgumentException("Parameter not implemented!");
        //todo manipulate adduct lists for marcus?????
    }

    @Option(names = {"--mostintense-ms2"}, description = "Only use the fragmentation spectrum with the most intense precursor peak (for each compound).", hidden = true)
    public boolean mostIntenseMs2;

    @Option(names = "--disable-fast-mode", hidden = true)
    public boolean disableFastMode;

    @Override
    public InstanceJob.Factory<SiriusSubToolJob> call() throws Exception {
        return () -> new SiriusSubToolJob(this);
    }


}
