package src.main.java.com.composer.core.domain.model;


import src.main.java.com.composer.core.domain.types.Duration;
import src.main.java.com.composer.core.domain.types.Interval;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Song implements Serializable {
  private static final long serialVersionUID = 6L; // Incremented tracker version

  public static class HarmonicNode implements Serializable {
    private static final long serialVersionUID = 2L;
    private final String id;
    private String parentId; // Made non-final to allow safe validation mutability
    private Interval interval; // Made non-final
    private int octaveShift;  // Made non-final
    private boolean inverted; // Made non-final
    private final String label;

    public HarmonicNode(String id, String parentId, Interval interval, int octaveShift, boolean inverted, String label) {
      this.id = id;
      this.parentId = parentId;
      this.interval = interval;
      this.octaveShift = octaveShift;
      this.inverted = inverted;
      this.label = label;
    }

    // Internal transaction worker setter package-private visibility
    void mutateProperties(String parentId, Interval interval, int octaveShift, boolean inverted) {
      this.parentId = parentId;
      this.interval = interval;
      this.octaveShift = octaveShift;
      this.inverted = inverted;
    }

    public String getId() {
      return id;
    }

    public String getParentId() {
      return parentId;
    }

    public Interval getInterval() {
      return interval;
    }

    public int getOctaveShift() {
      return octaveShift;
    }

    public boolean isInverted() {
      return inverted;
    }

    public String getLabel() {
      return label;
    }
  }

  public static class Chord implements Serializable {
    private static final long serialVersionUID = 4L;
    private final String chordId;
    private String name; // Made non-final to support edit mutations
    private final HarmonicNode rootNode;
    private final List<HarmonicNode> overtones = new ArrayList<>();
    private Duration duration; // Made non-final to support duration updates

    public Chord(String chordId, String name, HarmonicNode rootNode, Duration duration) {
      this.chordId = chordId;
      this.name = (name == null || name.isBlank()) ? "Chord " + chordId.replaceAll("\\D+", "") : name.trim();
      this.rootNode = rootNode;
      this.duration = duration;
    }

    void mutateMetadata(String name, Duration duration) {
      if (name != null && !name.isBlank()) this.name = name.trim();
      if (duration != null) this.duration = duration;
    }

    public String getChordId() {
      return chordId;
    }

    public String getName() {
      return name;
    }

    public HarmonicNode getRootNode() {
      return rootNode;
    }

    public List<HarmonicNode> getOvertones() {
      return overtones;
    }

    public Duration getDuration() {
      return duration;
    }
  }

  private String title = "Advanced Secure Relational Graph Score";
  private String author = "Graph Composer Expert";
  private int bpm = 120;
  private double referenceFrequency = 440.0;

  private final List<Chord> chords = new ArrayList<>();
  private transient List<Runnable> listeners = new ArrayList<>();

  public synchronized void addChordInstance(Chord chord) {
    chords.add(chord);
    notifyListeners();
  }

  public synchronized void removeChordInstance(Chord chord) {
    if (chords.remove(chord)) notifyListeners();
  }

  public synchronized void insertChordInstanceAt(Chord chord, int index) {
    if (index >= 0 && index <= chords.size()) {
      chords.add(index, chord);
      notifyListeners();
    }
  }

  public synchronized List<Chord> getChords() {
    return new ArrayList<>(chords);
  }

  public synchronized Chord findChordById(String id) {
    if (id == null) return null;
    for (Chord c : chords) {
      if (c.getChordId().equalsIgnoreCase(id)) return c;
    }
    return null;
  }

  public synchronized HarmonicNode findNodeById(String id) {
    if (id == null) return null;
    for (Chord c : chords) {
      if (c.getRootNode().getId().equalsIgnoreCase(id)) return c.getRootNode();
      for (HarmonicNode o : c.getOvertones()) {
        if (o.getId().equalsIgnoreCase(id)) return o;
      }
    }
    return null;
  }

  /**
   * CHORD ORDER INDEX FINDER
   * Returns the relative chronological placement index of a chord.
   */
  public synchronized int getChordChronologicalIndex(String chordId) {
    for (int i = 0; i < chords.size(); i++) {
      if (chords.get(i).getChordId().equalsIgnoreCase(chordId)) return i;
    }
    return -1;
  }

  /**
   * FIXED & SECURED SECURITY MANAGER: DEPENDENCY CHECKER
   * Comprehensive scan to prevent relational graph fracturing caused by case mismatch
   * or undetected overtone-to-overtone structural dependencies.
   */
  public synchronized List<String> verifyDeletionSafety(String chordId) {
    List<String> blockingDependencies = new ArrayList<>();
    Chord targetChord = findChordById(chordId);
    if (targetChord == null) return blockingDependencies;

    // 1. Collect all protected Node IDs belonging to this chord block in lowercase
    List<String> protectedIdsLowercase = new ArrayList<>();
    protectedIdsLowercase.add(targetChord.getRootNode().getId().toLowerCase());
    for (HarmonicNode o : targetChord.getOvertones()) {
      protectedIdsLowercase.add(o.getId().toLowerCase());
    }

    // 2. Scan EVERY subsequent chord block in the chronological tree layout timeline
    int chronologicalLimit = chords.indexOf(targetChord);
    for (int i = chronologicalLimit + 1; i < chords.size(); i++) {
      Chord subsequentChord = chords.get(i);

      // Check subsequent Tonic Root node dependency
      HarmonicNode subRoot = subsequentChord.getRootNode();
      if (subRoot.getParentId() != null && protectedIdsLowercase.contains(subRoot.getParentId().toLowerCase())) {
        blockingDependencies.add(String.format("Tonic %s of Chord %s (\"%s\")",
          subRoot.getId(), subsequentChord.getChordId(), subsequentChord.getName()));
      }

      // FIXED: Now we ALSO scan every subsequent Overtone node dependency to prevent ghost orphans
      for (HarmonicNode subOvertone : subsequentChord.getOvertones()) {
        if (subOvertone.getParentId() != null && protectedIdsLowercase.contains(subOvertone.getParentId().toLowerCase())) {
          blockingDependencies.add(String.format("Overtone %s of Chord %s (\"%s\")",
            subOvertone.getId(), subsequentChord.getChordId(), subsequentChord.getName()));
        }
      }
    }
    return blockingDependencies;
  }

  public synchronized int getNextChordIndex() {
    return chords.size() + 1;
  }

  public synchronized Map<String, Double> computeFrequencies() {
    Map<String, Double> calculated = new LinkedHashMap<>();
    for (Chord c : chords) {
      resolveNodeFrequency(c.getRootNode(), calculated);
      for (HarmonicNode o : c.getOvertones()) {
        resolveNodeFrequency(o, calculated);
      }
    }
    return calculated;
  }

  private void resolveNodeFrequency(HarmonicNode node, Map<String, Double> cache) {
    if (cache.containsKey(node.getId())) return;

    double parentFreq;
    if (node.getParentId() == null || !cache.containsKey(node.getParentId())) {
      HarmonicNode parentNode = findNodeById(node.getParentId());
      if (parentNode != null) {
        resolveNodeFrequency(parentNode, cache);
        parentFreq = cache.get(parentNode.getId());
      } else {
        parentFreq = this.referenceFrequency;
      }
    } else {
      parentFreq = cache.get(node.getParentId());
    }

    double coreRatio = node.getInterval().getRatioValue();
    if (node.isInverted() && coreRatio != 0) {
      coreRatio = 1.0 / coreRatio;
    }
    double octaveMultiplier = Math.pow(2.0, node.getOctaveShift());
    double freq = parentFreq * coreRatio * octaveMultiplier;
    cache.put(node.getId(), freq);
  }

  public synchronized String getTitle() {
    return title;
  }

  public synchronized void setTitle(String title) {
    this.title = title;
    notifyListeners();
  }

  public synchronized String getAuthor() {
    return author;
  }

  public synchronized void setAuthor(String author) {
    this.author = author;
    notifyListeners();
  }

  public synchronized int getBpm() {
    return bpm;
  }

  public synchronized void setBpm(int bpm) {
    this.bpm = bpm;
    notifyListeners();
  }

  public synchronized double getReferenceFrequency() {
    return referenceFrequency;
  }

  public synchronized void setReferenceFrequency(double referenceFrequency) {
    this.referenceFrequency = referenceFrequency;
    notifyListeners();
  }

  public synchronized void registerListener(Runnable listener) {
    if (listeners == null) listeners = new ArrayList<>();
    listeners.add(listener);
  }

  public void notifyListeners() {
    if (listeners == null) return;
    for (Runnable listener : listeners) listener.run();
  }

  public synchronized void copyFrom(Song other) {
    this.title = other.title;
    this.author = other.author;
    this.bpm = other.bpm;
    this.referenceFrequency = other.referenceFrequency;
    this.chords.clear();
    this.chords.addAll(other.chords);
    notifyListeners();
  }

  @Override
  public synchronized String toString() {
    if (chords.isEmpty()) return "--- Empty Structural Just Intonation DAG Score ---";

    Map<String, Double> frequencies = computeFrequencies();
    StringBuilder sb = new StringBuilder("=== RELATIVE MULTI-DIMENSIONAL JUST RATIO NETWORK ===\n");

    for (Chord c : chords) {
      // CORRECCIÓN: Se eliminó el String.format redundante y se pasaron los 4 argumentos correlativos a .formatted()
      sb.append("Chord %s: \"%s\" [Duration: %s, Time: %s beats]\n".formatted(
        c.getChordId(),
        c.getName(),
        c.getDuration().getVisualIcon(),
        c.getDuration().getBeatsValue()
      ));

      HarmonicNode r = c.getRootNode();
      sb.append(String.format("  └─ Tonic %s (Ref: %s, Int: %s, Octave: %+d, Inv: %b) -> %.2f Hz\n",
        r.getId(),
        (r.getParentId() == null ? "BASE" : r.getParentId()),
        r.getInterval().name() + "[" + r.getInterval().getExpression() + "]",
        r.getOctaveShift(),
        r.isInverted(),
        frequencies.getOrDefault(r.getId(), referenceFrequency)));

      for (HarmonicNode o : c.getOvertones()) {
        sb.append(String.format("      ├── Overtone %s (Ref: %s, Int: %s, Octave: %+d, Inv: %b) -> %.2f Hz\n",
          o.getId(),
          o.getParentId(),
          o.getInterval().name() + "[" + o.getInterval().getExpression() + "]",
          o.getOctaveShift(),
          o.isInverted(),
          frequencies.getOrDefault(o.getId(), referenceFrequency)));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}