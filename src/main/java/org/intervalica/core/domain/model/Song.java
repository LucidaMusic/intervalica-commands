package org.intervalica.core.domain.model;

import lombok.Getter;
import org.intervalica.core.domain.types.Duration;
import org.intervalica.core.domain.types.Interval;


import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Song implements Serializable {
  @Serial
  private static final long serialVersionUID = 7L; // Updated structural fingerprint tracking version

  @Getter
  public static class HarmonicNode implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    private final String id;
    private String parentId;
    private Interval interval;
    private int octaveShift;
    private boolean inverted;
    private final String label;

    public HarmonicNode(String id, String parentId, Interval interval, int octaveShift, boolean inverted, String label) {
      this.id = id;
      this.parentId = parentId;
      this.interval = interval;
      this.octaveShift = octaveShift;
      this.inverted = inverted;
      this.label = label;
    }

    void mutateProperties(String parentId, Interval interval, int octaveShift, boolean inverted) {
      this.parentId = parentId;
      this.interval = interval;
      this.octaveShift = octaveShift;
      this.inverted = inverted;
    }

  }

  @Getter
  public static class Chord implements Serializable {
    @Serial
    private static final long serialVersionUID = 4L;
    private final String chordId;
    private String name;
    private final HarmonicNode rootNode;
    private final List<HarmonicNode> overtones = new ArrayList<>();
    private Duration duration;

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

  public synchronized void clearAllChords() {
    chords.clear();
    notifyListeners();
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

  public synchronized Chord findChordByOvertoneId(String overtoneId) {
    if (overtoneId == null) return null;
    for (Chord c : chords) {
      for (HarmonicNode o : c.getOvertones()) {
        if (o.getId().equalsIgnoreCase(overtoneId)) return c;
      }
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

  public synchronized int getChordChronologicalIndex(String chordId) {
    for (int i = 0; i < chords.size(); i++) {
      if (chords.get(i).getChordId().equalsIgnoreCase(chordId)) return i;
    }
    return -1;
  }

  public synchronized List<String> verifyDeletionSafety(String chordId) {
    List<String> blockingDependencies = new ArrayList<>();
    Chord targetChord = findChordById(chordId);
    if (targetChord == null) return blockingDependencies;

    List<String> protectedIdsLowercase = new ArrayList<>();
    protectedIdsLowercase.add(targetChord.getRootNode().getId().toLowerCase());
    for (HarmonicNode o : targetChord.getOvertones()) {
      protectedIdsLowercase.add(o.getId().toLowerCase());
    }

    int chronologicalLimit = chords.indexOf(targetChord);
    for (int i = chronologicalLimit + 1; i < chords.size(); i++) {
      Chord subsequentChord = chords.get(i);

      HarmonicNode subRoot = subsequentChord.getRootNode();
      if (subRoot.getParentId() != null && protectedIdsLowercase.contains(subRoot.getParentId().toLowerCase())) {
        blockingDependencies.add(String.format("Tonic %s of Chord %s (\"%s\")",
          subRoot.getId(), subsequentChord.getChordId(), subsequentChord.getName()));
      }

      for (HarmonicNode subOvertone : subsequentChord.getOvertones()) {
        if (subOvertone.getParentId() != null && protectedIdsLowercase.contains(subOvertone.getParentId().toLowerCase())) {
          blockingDependencies.add(String.format("Overtone %s of Chord %s (\"%s\")",
            subOvertone.getId(), subsequentChord.getChordId(), subsequentChord.getName()));
        }
      }
    }
    return blockingDependencies;
  }

  /**
   * NEW INTEGRITY CHECKER: SPECIFIC OVERTONE DELETION SAFETY
   * Verifies if any subsequent node branches off a single isolated overtone before removing it.
   */
  public synchronized List<String> verifyOvertoneDeletionSafety(String overtoneId) {
    List<String> blockingDependencies = new ArrayList<>();
    if (overtoneId == null) return blockingDependencies;

    String targetLower = overtoneId.trim().toLowerCase();
    Chord parentChord = findChordByOvertoneId(overtoneId);
    if (parentChord == null) return blockingDependencies;

    int chronologicalLimit = chords.indexOf(parentChord);
    for (int i = chronologicalLimit + 1; i < chords.size(); i++) {
      Chord subsequentChord = chords.get(i);

      HarmonicNode subRoot = subsequentChord.getRootNode();
      if (subRoot.getParentId() != null && subRoot.getParentId().toLowerCase().equals(targetLower)) {
        blockingDependencies.add(String.format("Tonic %s of Chord %s (\"%s\")",
          subRoot.getId(), subsequentChord.getChordId(), subsequentChord.getName()));
      }

      for (HarmonicNode subOvertone : subsequentChord.getOvertones()) {
        if (subOvertone.getParentId() != null && subOvertone.getParentId().toLowerCase().equals(targetLower)) {
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
      sb.append("Chord %s: \"%s\" [Duration: %s, Time: %s beats]\n".formatted(c.getChordId(), c.getName(), c.getDuration().visualIcon(), c.getDuration().beatsValue()));
      HarmonicNode r = c.getRootNode();
      sb.append(String.format("  └─ Tonic %s (Ref: %s, Int: %s, Octave: %+d, Inv: %b) -> %.2f Hz\n", r.getId(), (r.getParentId() == null ? "BASE" : r.getParentId()), r.getInterval().name() + "[" + r.getInterval().getExpression() + "]", r.getOctaveShift(), r.isInverted(), frequencies.getOrDefault(r.getId(), referenceFrequency)));
      for (HarmonicNode o : c.getOvertones()) {
        sb.append(String.format("      ├── Overtone %s (Ref: %s, Int: %s, Octave: %+d, Inv: %b) -> %.2f Hz\n", o.getId(), o.getParentId(), o.getInterval().name() + "[" + o.getInterval().getExpression() + "]", o.getOctaveShift(), o.isInverted(), frequencies.getOrDefault(o.getId(), referenceFrequency)));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}