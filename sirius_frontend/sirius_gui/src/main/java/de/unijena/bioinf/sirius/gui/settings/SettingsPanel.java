package de.unijena.bioinf.sirius.gui.settings;/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 07.10.16.
 */

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public interface SettingsPanel {
//    public Properties getProperties();
    public void refreshValues ();
    public void saveProperties();
    public String name();
}