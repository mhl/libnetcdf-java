package ucar.nc2.ui;

import ucar.nc2.grib.table.WmoTemplateTable;
import ucar.nc2.ui.widget.BAMutil;
import ucar.nc2.ui.widget.FileManager;
import ucar.nc2.ui.widget.IndependentWindow;
import ucar.nc2.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.ui.BeanTableSorted;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;

/**
 * Describe
 *
 * @author caron
 * @since Aug 27, 2010
 */
public class GribWmoTemplatesPanel extends JPanel {
  private PreferencesExt prefs;

  private BeanTableSorted codeTable, entryTable;
  private JSplitPane split, split2;

  private TextHistoryPane compareTA;
  private IndependentWindow infoWindow;

  private FileManager fileChooser;

  public GribWmoTemplatesPanel(final PreferencesExt prefs, JPanel buttPanel) {
    this.prefs = prefs;

    codeTable = new BeanTableSorted(CodeBean.class, (PreferencesExt) prefs.node("CodeBean"), false);
    codeTable.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        CodeBean csb = (CodeBean) codeTable.getSelectedBean();
        setEntries(csb.template);
      }
    });

    entryTable = new BeanTableSorted(EntryBean.class, (PreferencesExt) prefs.node("EntryBean"), false);
    entryTable.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        EntryBean csb = (EntryBean) entryTable.getSelectedBean();
      }
    });

    ucar.nc2.ui.widget.PopupMenu varPopup = new ucar.nc2.ui.widget.PopupMenu(codeTable.getJTable(), "Options");
    varPopup.addAction("Show table", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Formatter out = new Formatter();
        CodeBean csb = (CodeBean) codeTable.getSelectedBean();
        csb.showTable(out);
        compareTA.setText(out.toString());
        compareTA.gotoTop();
        infoWindow.setVisible(true);
      }
    });

    // the info window
    compareTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("netcdfUI"), compareTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 600)));

    split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, codeTable, entryTable);
    split.setDividerLocation(prefs.getInt("splitPos", 500));

    setLayout(new BorderLayout());
    add(split, BorderLayout.CENTER);

    ///

    try {
      java.util.List<WmoTemplateTable> codes = WmoTemplateTable.getWmoStandard().list;
      java.util.List<CodeBean> dds = new ArrayList<CodeBean>(codes.size());
      for (WmoTemplateTable code : codes) {
        dds.add(new CodeBean(code));
      }
      codeTable.setBeans(dds);

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void save() {
    codeTable.saveState(false);
    entryTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    //prefs.putBeanObject("InfoWindowBounds2", infoWindow2.getBounds());
    prefs.putInt("splitPos", split.getDividerLocation());
    //prefs.putInt("splitPos2", split2.getDividerLocation());
    if (fileChooser != null) fileChooser.save();
  }

  public void setEntries(WmoTemplateTable template) {
    java.util.List<EntryBean> beans = new ArrayList<EntryBean>(template.flds.size());
    for (WmoTemplateTable.Field d : template.flds) {
      beans.add(new EntryBean(d));
    }
    entryTable.setBeans(beans);
  }

  public class CodeBean {
    WmoTemplateTable template;

    // no-arg constructor
    public CodeBean() {
    }

    // create from a dataset
    public CodeBean(WmoTemplateTable template) {
      this.template = template;
    }

    public String getName() {
      return template.name;
    }

    public String getDescription() {
      return template.desc;
    }

    public int getM1() {
      return template.m1;
    }

    public int getM2() {
      return template.m2;
    }

    void showTable(Formatter f) {
      f.format("Template %s (%s)%n", template.name, template.desc);
      for (WmoTemplateTable.Field entry : template.flds) {
        f.format("  %6s (%d): %s", entry.octet, entry.nbytes, entry.content);
        if (entry.note != null)
          f.format(" - %s", entry.note);
        f.format("%n");
      }
    }

  }

  public class EntryBean {
    WmoTemplateTable.Field te;

    // no-arg constructor
    public EntryBean() {
    }

    // create from a dataset
    public EntryBean(WmoTemplateTable.Field te) {
      this.te = te;
    }

    public String getOctet() {
      return te.octet;
    }

    public String getContent() {
      return te.content;
    }

    public int getNbytes() {
      return te.nbytes;
    }

    public int getStart() {
      return te.start;
    }

    public String getStatus() {
      return te.status;
    }

    public String getNotes() {
      return te.note;
    }

  }
}
  