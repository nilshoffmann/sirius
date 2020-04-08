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

package de.unijena.bioinf.ms.gui.compute;

import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.FragmentationTreeConstruction.computation.tree.TreeBuilder;
import de.unijena.bioinf.FragmentationTreeConstruction.computation.tree.TreeBuilderFactory;
import de.unijena.bioinf.jjobs.TinyBackgroundJJob;
import de.unijena.bioinf.ms.frontend.Run;
import de.unijena.bioinf.ms.frontend.subtools.config.DefaultParameterConfigLoader;
import de.unijena.bioinf.ms.frontend.subtools.gui.GuiComputeRoot;
import de.unijena.bioinf.ms.frontend.workflow.WorkflowBuilder;
import de.unijena.bioinf.ms.frontend.workfow.GuiInstanceBufferFactory;
import de.unijena.bioinf.ms.gui.actions.CheckConnectionAction;
import de.unijena.bioinf.ms.gui.compute.jjobs.Jobs;
import de.unijena.bioinf.ms.gui.dialogs.ErrorReportDialog;
import de.unijena.bioinf.ms.gui.dialogs.QuestionDialog;
import de.unijena.bioinf.ms.gui.dialogs.WarningDialog;
import de.unijena.bioinf.ms.gui.dialogs.WorkerWarningDialog;
import de.unijena.bioinf.ms.gui.io.LoadController;
import de.unijena.bioinf.ms.gui.mainframe.MainFrame;
import de.unijena.bioinf.ms.gui.net.ConnectionMonitor;
import de.unijena.bioinf.ms.gui.utils.ExperimentEditPanel;
import de.unijena.bioinf.ms.properties.PropertyManager;
import de.unijena.bioinf.projectspace.InstanceBean;
import de.unijena.bioinf.sirius.Sirius;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.unijena.bioinf.ms.gui.mainframe.MainFrame.MF;

public class BatchComputeDialog extends JDialog /*implements ActionListener*/ {
    public static final String DONT_ASK_RECOMPUTE_KEY = "de.unijena.bioinf.sirius.computeDialog.recompute.dontAskAgain";

    // main parts
    private ExperimentEditPanel editPanel;
    private final Box mainPanel;
    private final JCheckBox recompute;

    // tool configurations
    private final ActFormulaIDConfigPanel formulaIDConfigPanel; //Sirius configs
    private final ActZodiacConfigPanel zodiacConfigs; //Zodiac configs
    private final ActFingerIDConfigPanel csiConfigs; //FingerID configs
    private final ActCanopusConfigPanel canopusConfigPanel; //Canopus configs

    // compounds on which the configured Run will be executed
    private final List<InstanceBean> compoundsToProcess;

    public BatchComputeDialog(MainFrame owner, List<InstanceBean> compoundsToProcess) {
        super(owner, "compute", true);
        {
            this.compoundsToProcess = compoundsToProcess;

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());

            mainPanel = Box.createVerticalBox();
            add(mainPanel, BorderLayout.CENTER);
        }


        {
            // make subtool config panels
            formulaIDConfigPanel = new ActFormulaIDConfigPanel(this, compoundsToProcess);
            addConfigPanel("SIRIUS - Molecular Formula Identification", formulaIDConfigPanel);

            zodiacConfigs = new ActZodiacConfigPanel();
            addConfigPanel("ZODIAC - Network-based improvement of SIRIUS molecular formula ranking", zodiacConfigs);

            csiConfigs = new ActFingerIDConfigPanel(formulaIDConfigPanel.content.ionizationList.checkBoxList);
            addConfigPanel("CSI:FingerID - Structure Elucidation", csiConfigs);

            canopusConfigPanel = new ActCanopusConfigPanel();
            addConfigPanel("CANOPUS - Compound Class Prediction", canopusConfigPanel);

            //Make edit panel for single compound mode if needed
            if (compoundsToProcess.size() == 1)
                initSingleExperimentDialog();
        }
        // make south panel with Recompute/Compute/Abort
        {
            JPanel southPanel = new JPanel();
            southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.LINE_AXIS));

            JPanel lsouthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            recompute = new JCheckBox("Recompute already computed tasks?", false);
            recompute.setToolTipText("If checked, all selected compounds will be computed. Already computed analysis steps will be recomputed.");
            lsouthPanel.add(recompute);

            //checkConnectionToUrl by default when just one experiment is selected
            if (compoundsToProcess.size() == 1) recompute.setSelected(true);

            JPanel rsouthPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
            JButton compute = new JButton("Compute");
            compute.addActionListener(e -> startComputing());
            JButton abort = new JButton("Abort");
            abort.addActionListener(e -> dispose());
            rsouthPanel.add(compute);
            rsouthPanel.add(abort);

            southPanel.add(lsouthPanel);
            southPanel.add(rsouthPanel);

            this.add(southPanel, BorderLayout.SOUTH);
        }

        //finalize panel build
        configureActions();
        pack();
        setResizable(false);
        setLocationRelativeTo(getParent());
        setVisible(true);
    }

    private void addConfigPanel(String header, JPanel configPanel) {
        JPanel stack = new JPanel();
        stack.setLayout(new BorderLayout());
        stack.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), header));
        stack.add(configPanel, BorderLayout.CENTER);
        mainPanel.add(stack);
    }

    private void configureActions() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke enterKey = KeyStroke.getKeyStroke("ENTER");
        KeyStroke escKey = KeyStroke.getKeyStroke("ESCAPE");
        String enterAction = "compute";
        String escAction = "abort";
        inputMap.put(enterKey, enterAction);
        inputMap.put(escKey, escAction);
        getRootPane().getActionMap().put(enterAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startComputing();
            }
        });
        getRootPane().getActionMap().put(escAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                abortComputing();
            }
        });
    }

    private void abortComputing() {
        this.dispose();
    }

    private void saveEdits(InstanceBean ec) {
        Jobs.runInBackgroundAndLoad(this, "Saving changes...", () ->
                LoadController.completeExisting(ec, editPanel));
    }

    private void startComputing() {
        if (editPanel != null && compoundsToProcess.size() == 1)
            saveEdits(compoundsToProcess.get(0));

        if (recompute.isSelected()) {
            boolean recompute = false;
            if (!PropertyManager.getBoolean(DONT_ASK_RECOMPUTE_KEY, false) && this.compoundsToProcess.size() > 1) {
                QuestionDialog questionDialog = new QuestionDialog(this, "<html><body>Do you really want to recompute already computed experiments? <br> All existing results will be lost!</body></html>", DONT_ASK_RECOMPUTE_KEY);
                recompute = questionDialog.isSuccess();
            }
            //todo implement compute state handling
        }


        Jobs.runInBackgroundAndLoad(getOwner(), "Submitting Identification Jobs", new TinyBackgroundJJob<>() {
            @Override
            protected Boolean compute() throws InterruptedException {
                updateProgress(0, 100, 0, "Configuring Computation...");
                checkForInterruption();

                // CHECK ILP SOLVER
                TreeBuilder builder = new Sirius().getMs2Analyzer().getTreeBuilder();
                if (builder == null) {
                    String noILPSolver = "Could not load a valid TreeBuilder (ILP solvers) " + Arrays.toString(TreeBuilderFactory.getBuilderPriorities()) + ". Please read the installation instructions.";
                    LoggerFactory.getLogger(BatchComputeDialog.class).error(noILPSolver);
                    new ErrorReportDialog(BatchComputeDialog.this, noILPSolver);
                    dispose();
                    return false;
                }
                LoggerFactory.getLogger(this.getClass()).info("Compute trees using " + builder);
                updateProgress(0, 100, 1, "ILP solver check DONE!");
                checkForInterruption();

                //CHECK worker availability
                checkConnection();

                updateProgress(0, 100, 2, "Connection check DONE!");
                checkForInterruption();

                try {
                    // create computation parameters
                    List<String> toolCommands = new ArrayList<>();
                    List<String> configCommand = new ArrayList<>();

                    configCommand.add("config");
                    if (formulaIDConfigPanel.isToolSelected()) {
                        toolCommands.add(formulaIDConfigPanel.content.toolCommand());
                        configCommand.addAll(formulaIDConfigPanel.asParameterList());
                    }

                    if (zodiacConfigs.isToolSelected()) {
                        toolCommands.add(zodiacConfigs.content.toolCommand());
                        configCommand.addAll(zodiacConfigs.asParameterList());
                    }

                    if (csiConfigs.isToolSelected()) {
                        toolCommands.add(csiConfigs.content.toolCommand());
                        configCommand.addAll(csiConfigs.asParameterList());
                    }

                    if (canopusConfigPanel.isToolSelected()) {
                        toolCommands.add(canopusConfigPanel.content.toolCommand());
                        configCommand.addAll(canopusConfigPanel.asParameterList());
                    }

                    final List<String> command = new ArrayList<>();

                    configCommand.add("--RecomputeResults");
                    configCommand.add(String.valueOf(recompute.isSelected()));

                    command.addAll(configCommand);
                    command.addAll(toolCommands);

                    final DefaultParameterConfigLoader configOptionLoader = new DefaultParameterConfigLoader();
                    final WorkflowBuilder<GuiComputeRoot> wfBuilder = new WorkflowBuilder<>(new GuiComputeRoot(MF.ps(), compoundsToProcess), configOptionLoader, new GuiInstanceBufferFactory());
                    final Run computation = new Run(wfBuilder);

                    computation.parseArgs(command.toArray(String[]::new));
                    if (computation.isWorkflowDefined())
                        Jobs.runInBackground(computation::compute);//todo make som nice head job that does some organizing stuff
                    //todo else some error message with pico cli output
                } catch (IOException e) {
                    e.printStackTrace();
                }

                updateProgress(0, 100, 100, "Computation Configured!");
                return true;
            }
        });
        dispose();
    }

    private void checkConnection() {
        final @Nullable ConnectionMonitor.ConnetionCheck cc = CheckConnectionAction.checkConnectionAndLoad();

        if (cc != null) {
            if (cc.isConnected()) {
                if (csiConfigs.isToolSelected() && cc.hasWorkerWarning()) {
                    new WorkerWarningDialog(MF, cc.workerInfo == null);
                }
            } else {
                if (formulaIDConfigPanel.content.getFormulaSearchDBs() != null) {
                    new WarnFormulaSourceDialog(MF);
                    formulaIDConfigPanel.content.searchDBList.checkBoxList.uncheckAll();
                }
            }
        } else {
            if (formulaIDConfigPanel.content.getFormulaSearchDBs() != null) {
                new WarnFormulaSourceDialog(MF);
//                formulaIDConfigPanel.formulaCombobox.setSelectedIndex(0); //todo set NONE
            }
        }
    }

    public void initSingleExperimentDialog() {
        JPanel north = new JPanel(new BorderLayout());

        InstanceBean ec = compoundsToProcess.get(0);
        editPanel = new ExperimentEditPanel();
        editPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Edit Input Data"));
        north.add(editPanel, BorderLayout.NORTH);

        //todo beging ugly hack --> we want to manage this by the edit panel instead and fire edit panel events
        editPanel.formulaTF.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                boolean enable = e.getDocument().getLength() == 0;
                formulaIDConfigPanel.content.searchDBList.setEnabled(enable);
                formulaIDConfigPanel.content.candidatesSpinner.setEnabled(enable);
                formulaIDConfigPanel.content.candidatesPerIonSpinner.setEnabled(enable);
            }
        });

        editPanel.ionizationCB.addActionListener(e -> {
            PrecursorIonType ionType = editPanel.getSelectedIonization();
            formulaIDConfigPanel.content.refreshPossibleIonizations(Collections.singleton(ionType.getIonization().getName()));
            pack();
        });


        csiConfigs.content.adductOptions.checkBoxList.addPropertyChangeListener("refresh", evt -> {
            PrecursorIonType ionType = editPanel.getSelectedIonization();
            if (!ionType.getAdduct().isEmpty()) {
                csiConfigs.content.adductOptions.checkBoxList.uncheckAll();
                csiConfigs.content.adductOptions.checkBoxList.check(ionType.toString());
                csiConfigs.content.adductOptions.setEnabled(false);
            } else {
                csiConfigs.content.adductOptions.setEnabled(csiConfigs.isToolSelected());
            }
        });

//        searchProfilePanel.refreshPossibleIonizations(Collections.singleton(editPanel.getSelectedIonization().getIonization().getName()));
        editPanel.setData(ec);
        /////// todo ugly hack end
        add(north, BorderLayout.NORTH);
    }

    private static class WarnFormulaSourceDialog extends WarningDialog {
        private final static String DONT_ASK_KEY = PropertyManager.PROPERTY_BASE + ".sirius.computeDialog.formulaSourceWarning.dontAskAgain";
        public static final String FORMULA_SOURCE_WARNING_MESSAGE =
                "<b>Warning:</b> No connection to webservice available! <br>" +
                        "Online databases cannot be used for formula identification.<br> " +
                        "If online databases are selected, the default option <br>" +
                        "(all molecular formulas) will be used instead.";

        public WarnFormulaSourceDialog(Frame owner) {
            super(owner, "<html>" + FORMULA_SOURCE_WARNING_MESSAGE, DONT_ASK_KEY + "</html>");
        }
    }
}