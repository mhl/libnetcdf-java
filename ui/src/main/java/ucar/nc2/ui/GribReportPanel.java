package ucar.nc2.ui;

import thredds.inventory.CollectionManager;
import thredds.inventory.DatasetCollectionMFiles;
import thredds.inventory.MFile;
import ucar.nc2.Attribute;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.grib.GribCollection;
import ucar.nc2.grib.GribCollectionBuilder;
import ucar.nc2.grib.grib2.*;
import ucar.nc2.grib.grib2.Grib2Pds;
import ucar.nc2.grib.table.WmoCodeTable;
import ucar.nc2.ui.widget.TextHistoryPane;
import ucar.nc2.util.Misc;
import ucar.unidata.io.RandomAccessFile;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Run through Grib files and make reports
 *
 * @author caron
 * @since Dec 13, 2010
 */
public class GribReportPanel extends JPanel {
  public static enum Report {
    checkTables, localUseSection, uniqueGds, duplicatePds, drsSummary, gdsTemplate, pdsSummary, idProblems
  }

  private PreferencesExt prefs;
  private TextHistoryPane reportPane;

  public GribReportPanel(PreferencesExt prefs, JPanel buttPanel) {
    this.prefs = prefs;
    reportPane = new TextHistoryPane();
    setLayout(new BorderLayout());
    add(reportPane, BorderLayout.CENTER);
  }

  public void save() {
  }

  public void showInfo(Formatter f) {
  }

  public boolean setCollection(String spec) {
    Formatter f = new Formatter();
    f.format("collection = %s%n", spec);
    boolean hasFiles = false;

    CollectionManager dcm = getCollection(spec, f);
    if (dcm == null) {
      return false;
    }

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      hasFiles = true;
    }

    reportPane.setText(f.toString());
    reportPane.gotoTop();
    return hasFiles;
  }

  private CollectionManager getCollection(String spec, Formatter f) {
    CollectionManager dc = null;
    try {
      dc = DatasetCollectionMFiles.open(spec, null, f);
      dc.scan();

    } catch (Exception e) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(10000);
      e.printStackTrace(new PrintStream(bos));
      reportPane.setText(bos.toString());
      return null;
    }

    return dc;
  }

  public void doReport(String spec, boolean useIndex, Report which) throws IOException {
    Formatter f = new Formatter();
    f.format("%s %s %s%n", spec, useIndex, which);

    CollectionManager dcm = getCollection(spec, f);
    if (dcm == null) {
      return;
    }

    // CollectionSpecParser parser = dcm.getCollectionSpecParser();

    f.format("top dir = %s%n", dcm.getRoot());
    //f.format("filter = %s%n", parser.getFilter());
    reportPane.setText(f.toString());

    File top = new File(dcm.getRoot());
    if (!top.exists()) {
      f.format("top dir = %s does not exist%n", dcm.getRoot());
    } else {

      switch (which) {
        case checkTables:
          doCheckTables(f, dcm, useIndex);
          break;
        case localUseSection:
          doLocalUseSection(f, dcm, useIndex);
          break;
        case uniqueGds:
          doUniqueGds(f, dcm, useIndex);
          break;
        case duplicatePds:
          doDuplicatePds(f, dcm, useIndex);
          break;
        case drsSummary:
          doDrsSummary(f, dcm, useIndex);
          break;
        case gdsTemplate:
          doGdsTemplate(f, dcm, useIndex);
          break;
        case pdsSummary:
          doPdsSummary(f, dcm, useIndex);
          break;
        case idProblems:
          doIdProblems(f, dcm, useIndex);
          break;
      }
    }

    reportPane.setText(f.toString());
    reportPane.gotoTop();
  }

  ///////////////////////////////////////////////

  private void doCheckTables(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Check Grib-2 Parameter Tables%n");
    int[] accum = new int[4];

    for (MFile mfile : dcm.getFiles()) {
      f.format("%n %s%n", mfile.getPath());
      doCheckTables(mfile, f, accum);
    }

    f.format("%nGrand total=%d not operational = %d local = %d missing = %d%n", accum[0], accum[1], accum[2], accum[3]);
  }

  private void doCheckTables(MFile ff, Formatter fm, int[] accum) throws IOException {
    int local = 0;
    int miss = 0;
    int nonop = 0;
    int total = 0;

    GridDataset ncfile = null;
    try {
      ncfile = GridDataset.open(ff.getPath());
      for (GridDatatype dt : ncfile.getGrids()) {
        String currName = dt.getName();
        total++;

        Attribute att = dt.findAttributeIgnoreCase("GRIB_param_id");
        int discipline = (Integer) att.getValue(1);
        int category = (Integer) att.getValue(2);
        int number = (Integer) att.getValue(3);
        if (number >= 192) {
          fm.format("  local parameter = %s (%d %d %d) units=%s %n", currName, discipline, category, number, dt.getUnitsString());
          local++;
          continue;
        }

        WmoCodeTable.TableEntry entry = WmoCodeTable.getParameterEntry(discipline, category, number);
        if (entry == null) {
          fm.format("  Missing parameter = %s (%d %d %d) %n", currName, discipline, category, number);
          miss++;
          continue;
        }

        if (!entry.status.equalsIgnoreCase("Operational")) {
          fm.format("  %s parameter = %s (%d %d %d) %n", entry.status, currName, discipline, category, number);
          nonop++;
        }

      }
    } finally {
      if (ncfile != null) ncfile.close();
    }
    fm.format("total=%d not operational = %d local = %d missing = %d%n", total, nonop, local, miss);
    accum[0] += total;
    accum[1] += nonop;
    accum[2] += local;
    accum[3] += miss;
  }

  ///////////////////////////////////////////////

  private void doLocalUseSection(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Local Use Section%n");

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doLocalUseSection(mfile, f, useIndex);
    }
  }

  private void doLocalUseSection(MFile mf, Formatter f, boolean useIndex) throws IOException {
    f.format("File = %s%n", mf);

    String path = mf.getPath();
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, mf.getLastModified()))
      index.makeIndex(path, f);

    for (Grib2Record gr : index.getRecords()) {
      Grib2SectionLocalUse lus = gr.getLocalUseSection();
      if (lus == null || lus.getRawBytes() == null)
        f.format(" %10d == none%n", gr.getDataSection().getStartingPosition());
      else
        f.format(" %10d == %s%n", gr.getDataSection().getStartingPosition(), Misc.showBytes(lus.getRawBytes()));
    }
  }

  ///////////////////////////////////////////////

  private void doUniqueGds(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Unique GDS%n");

    Map<Integer, GdsList> gdsSet = new HashMap<Integer, GdsList>();
    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doUniqueGds(mfile, gdsSet, f);
    }

    for (GdsList gdsl : gdsSet.values()) {
      f.format("%nGDS = %d x %d (%d) %n", gdsl.gds.ny, gdsl.gds.nx, gdsl.gds.template);
      for (FileCount fc : gdsl.fileList)
        f.format("  %5d %s (%d)%n", fc.count, fc.f.getPath(), fc.countGds);
    }
  }

  private void doUniqueGds(MFile mf, Map<Integer, GdsList> gdsSet, Formatter f) throws IOException {
    String path = mf.getPath();
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, mf.getLastModified()))
      index.makeIndex(path, f);

    int countGds = index.getGds().size();
    for (Grib2Record gr : index.getRecords()) {
      int hash = gr.getGDSsection().getGDS().hashCode();
      GdsList gdsList = gdsSet.get(hash);
      if (gdsList == null) {
        gdsList = new GdsList(gr.getGDSsection().getGDS());
        gdsSet.put(hash, gdsList);
      }
      FileCount fc = gdsList.contains(mf);
      if (fc == null) {
        fc = new FileCount(mf, countGds);
        gdsList.fileList.add(fc);
      }
      fc.count++;
    }
  }

  private class GdsList {
    Grib2Gds gds;
    java.util.List<FileCount> fileList = new ArrayList<FileCount>();

    private GdsList(Grib2Gds gds) {
      this.gds = gds;
    }

    FileCount contains(MFile f) {
      for (FileCount fc : fileList)
        if (fc.f.getPath().equals(f.getPath())) return fc;
      return null;
    }

  }

  private class FileCount {
    private FileCount(MFile f, int countGds) {
      this.f = f;
      this.countGds = countGds;
    }

    MFile f;
    int count = 0;
    int countGds = 0;
  }

  ///////////////////////////////////////////////

  private int countPDS, countPDSdup;

  private void doDuplicatePds(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    countPDS = 0;
    countPDSdup = 0;
    f.format("Show Duplicate PDS%n");
    for (MFile mfile : dcm.getFiles()) {
      doDuplicatePds(f, mfile);
    }
    f.format("Total PDS duplicates = %d / %d%n%n", countPDSdup, countPDS);
  }

  private void doDuplicatePds(Formatter f, MFile mfile) throws IOException {
    Set<Long> pdsMap = new HashSet<Long>();
    int dups = 0;
    int count = 0;

    RandomAccessFile raf = new RandomAccessFile(mfile.getPath(), "r");
    Grib2RecordScanner scan = new Grib2RecordScanner(raf);
    while (scan.hasNext()) {
      ucar.nc2.grib.grib2.Grib2Record gr = scan.next();
      Grib2SectionProductDefinition pds = gr.getPDSsection();
      long crc = pds.calcCRC();
      if (pdsMap.contains(crc))
        dups++;
      else
        pdsMap.add(crc);
      count++;
    }
    raf.close();

    f.format("PDS duplicates = %d / %d for %s%n%n", dups, count, mfile.getPath());
    countPDS += count;
    countPDSdup += dups;
  }

  ///////////////////////////////////////////////

  private class Counter {
    Map<Integer, Integer> set = new HashMap<Integer, Integer>();
    String name;

    private Counter(String name) {
      this.name = name;
    }

    void count(int value) {
      Integer count = set.get(value);
      if (count == null)
        set.put(value, 1);
      else
        set.put(value, count + 1);
    }

    void show(Formatter f) {
      f.format("%n%s%n", name);
      List<Integer> list = new ArrayList<Integer>(set.keySet());
      Collections.sort(list);
      for (int template : list) {
        int count = set.get(template);
        f.format("   %3d: count = %d%n", template, count);
      }
    }

  }

  int total = 0;
  int prob = 0;

  private void doPdsSummary(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    if (useIndex) {
      f.format("Check Grib-2 PDS probability and statistical variables%n");
      total = 0;
      prob = 0;
      for (MFile mfile : dcm.getFiles()) {
        f.format("%n %s%n", mfile.getPath());
        doPdsSummaryIndexed(f, mfile);
      }
      f.format("problems = %d/%d%n", prob, total);

    } else {
      Counter templateSet = new Counter("template");
      Counter timeUnitSet = new Counter("timeUnit");
      Counter statTypeSet = new Counter("statType");
      Counter NTimeIntervals = new Counter("NTimeIntervals");
      Counter processId = new Counter("processId");

      for (MFile mfile : dcm.getFiles()) {
        f.format(" %s%n", mfile.getPath());
        doPdsSummary(f, mfile, templateSet, timeUnitSet, statTypeSet, NTimeIntervals, processId);
      }

      templateSet.show(f);
      timeUnitSet.show(f);
      statTypeSet.show(f);
      NTimeIntervals.show(f);
      processId.show(f);
    }
  }

  private void doPdsSummaryIndexed(Formatter fm, MFile ff) throws IOException {
    String path = ff.getPath();

    //make sure indexes exist
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, ff.getLastModified()))
      index.makeIndex(path, fm);
    GribCollection gc = GribCollectionBuilder.createFromSingleFile(new File(path), fm);
    gc.close();

    GridDataset ncfile = null;
    try {
      ncfile = GridDataset.open(path + GribCollection.IDX_EXT);
      for (GridDatatype dt : ncfile.getGrids()) {
        String currName = dt.getName();
        total++;

        Attribute att = dt.findAttributeIgnoreCase("Grib_Probability_Type");
        if (att != null) {
          fm.format("  %s (PROB) desc=%s %n", currName, dt.getDescription());
          prob++;
        }

        att = dt.findAttributeIgnoreCase("Grib_Statistical_Interval_Type");
        if (att != null) {
          int statType = att.getNumericValue().intValue();
          if ((statType == 7) || (statType == 9)) {
            fm.format("  %s (STAT type %s) desc=%s %n", currName, statType, dt.getDescription());
            prob++;
          }
        }

        att = dt.findAttributeIgnoreCase("Grib_Ensemble_Derived_Type");
        if (att != null) {
          int type = att.getNumericValue().intValue();
          if ((type > 9)) {
            fm.format("  %s (DERIVED type %s) desc=%s %n", currName, type, dt.getDescription());
            prob++;
          }
        }

      }
    } catch (Throwable ioe) {
      fm.format("Failed on %s == %s%n", path, ioe.getMessage());
      System.out.printf("Failed on %s%n", path);
      ioe.printStackTrace();

    } finally {
      if (ncfile != null) ncfile.close();
    }
  }

  private void doPdsSummary(Formatter f, MFile mf, Counter templateSet, Counter timeUnitSet, Counter statTypeSet, Counter NTimeIntervals,
                            Counter processId) throws IOException {
    String path = mf.getPath();
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, mf.getLastModified()))
      index.makeIndex(path, f);

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2Pds pds = gr.getPDS();
      templateSet.count(pds.getTemplateNumber());
      timeUnitSet.count(pds.getTimeUnit());
      processId.count(pds.getGenProcessId());

      if (pds instanceof Grib2Pds.PdsInterval) {
        Grib2Pds.PdsInterval pdsi = (Grib2Pds.PdsInterval) pds;
        for (Grib2Pds.TimeInterval ti : pdsi.getTimeIntervals())
          statTypeSet.count(ti.statProcessType);
        NTimeIntervals.count(pdsi.getTimeIntervals().length);
      }
    }
  }

  ///////////////////////////////////////////////

  private void doIdProblems(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Look for ID Problems%n");

    Counter disciplineSet = new Counter("discipline");
    Counter masterTable = new Counter("masterTable");
    Counter localTable = new Counter("localTable");
    Counter centerId = new Counter("centerId");
    Counter subcenterId = new Counter("subcenterId");
    Counter genProcess = new Counter("genProcess");
    Counter backProcess = new Counter("backProcess");

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doIdProblems(f, mfile, useIndex,
              disciplineSet, masterTable, localTable, centerId, subcenterId, genProcess, backProcess);
    }

    disciplineSet.show(f);
    masterTable.show(f);
    localTable.show(f);
    centerId.show(f);
    subcenterId.show(f);
    genProcess.show(f);
    backProcess.show(f);
  }

  private void doIdProblems(Formatter f, MFile mf, boolean showProblems,
                            Counter disciplineSet, Counter masterTable, Counter localTable, Counter centerId,
                            Counter subcenterId, Counter genProcessC, Counter backProcessC) throws IOException {
    String path = mf.getPath();
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, mf.getLastModified()))
      index.makeIndex(path, f);

    // these should be the same for the entire file
    int center = -1;
    int subcenter = -1;
    int master = -1;
    int local = -1;
    int genProcess = -1;
    int backProcess = -1;

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      disciplineSet.count(gr.getDiscipline());
      masterTable.count(gr.getId().getMaster_table_version());
      localTable.count(gr.getId().getLocal_table_version());
      centerId.count(gr.getId().getCenter_id());
      subcenterId.count(gr.getId().getSubcenter_id());
      genProcessC.count(gr.getPDS().getGenProcessId());
      backProcessC.count(gr.getPDS().getBackProcessId());

      if (!showProblems) continue;

      if (gr.getDiscipline() == 255) {
        f.format("  bad discipline= ");
        gr.show(f);
        f.format("%n");
      }

      int val = gr.getId().getCenter_id();
      if (center < 0) center = val;
      else if (center != val) {
        f.format("  center %d != %d ", center, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getSubcenter_id();
      if (subcenter < 0) subcenter = val;
      else if (subcenter != val) {
        f.format("  subcenter %d != %d ", subcenter, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getMaster_table_version();
      if (master < 0) master = val;
      else if (master != val) {
        f.format("  master %d != %d ", master, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getLocal_table_version();
      if (local < 0) local = val;
      else if (local != val) {
        f.format("  local %d != %d ", local, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getPDS().getGenProcessId();
      if (genProcess < 0) genProcess = val;
      else if (genProcess != val) {
        f.format("  genProcess %d != %d ", genProcess, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getPDS().getBackProcessId();
      if (backProcess < 0) backProcess = val;
      else if (backProcess != val) {
        f.format("  backProcess %d != %d ", backProcess, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

    }
  }

  ///////////////////////////////////////////////

  private void doDrsSummary(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Unique DRS Templates%n");
    Counter template = new Counter("DRS template");
    Counter prob = new Counter("DRS template 40 signed problem");

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doDrsSummary(f, mfile, useIndex, template, prob);
    }

    template.show(f);
    prob.show(f);
  }

  private void doDrsSummary(Formatter f, MFile mf, boolean useIndex, Counter templateC, Counter probC) throws IOException {
    String path = mf.getPath();
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, mf.getLastModified()))
      index.makeIndex(path, f);
    RandomAccessFile raf = new RandomAccessFile(path, "r");

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2SectionDataRepresentation drss = gr.getDataRepresentationSection();
      int template = drss.getDataTemplate();
      templateC.count(template);

      if (useIndex && template == 40) {  // expensive
        Grib2Drs.Type40 drs40 = gr.readDataTest(raf);
        if (drs40 != null) {
          if (drs40.hasSignedProblem())
            probC.count(1);
          else
            probC.count(0);
        }
      }
    }
    raf.close();
  }

  ///////////////////////////////////////////////

  private void doGdsTemplate(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Unique GDS Templates%n");

    Map<Integer, Integer> drsSet = new HashMap<Integer, Integer>();
    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doGdsTemplate(f, mfile, drsSet);
    }

    for (int template : drsSet.keySet()) {
      int count = drsSet.get(template);
      f.format("%nGDS template = %d count = %d%n", template, count);
    }
  }

  private void doGdsTemplate(Formatter f, MFile mf, Map<Integer, Integer> gdsSet) throws IOException {
    String path = mf.getPath();
    GribIndex index = new GribIndex();
    if (!index.readIndex(path, mf.getLastModified()))
      index.makeIndex(path, f);

    for (Grib2SectionGridDefinition gds : index.getGds()) {
      int template = gds.getGDSTemplateNumber();
      Integer count = gdsSet.get(template);
      if (count == null)
        gdsSet.put(template, 1);
      else
        gdsSet.put(template, count + 1);
    }
  }

}
