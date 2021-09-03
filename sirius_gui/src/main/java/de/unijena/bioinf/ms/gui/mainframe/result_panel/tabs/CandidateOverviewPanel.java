/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schiller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.ms.gui.mainframe.result_panel.tabs;

import de.unijena.bioinf.ms.gui.fingerid.StructureList;
import de.unijena.bioinf.ms.gui.fingerid.CandidateListStructureView;
import de.unijena.bioinf.ms.gui.fingerid.CandidateListTableView;
import de.unijena.bioinf.ms.gui.mainframe.result_panel.PanelDescription;

import javax.swing.*;
import java.awt.*;

/**
 * Created by fleisch on 15.05.17.
 */
public class CandidateOverviewPanel extends JPanel implements PanelDescription {
    @Override
    public String getDescription() {
        return "<html>"
                + "CSI:FingerID results for all molecular formulas that had been searched."
                +"<br>"
                + "Selected candidate structures are rendered in the bottom panel."
                + "</html>";
    }
    public CandidateOverviewPanel(final StructureList sourceList) {
        super(new BorderLayout());

        final CandidateListTableView north = new CandidateListTableView(sourceList);
        final CandidateListStructureView south = new CandidateListStructureView(north.getFilteredSelectionModel());

        add(north, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

}