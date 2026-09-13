package com.composer.core.domain.model;

import com.composer.core.domain.types.Duration;
import com.composer.core.domain.types.Interval;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Song implements Serializable {
    private static final long serialVersionUID = 2L;

    public static class HarmonicNode implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id;
        private final String parentId; // Si es nulo, referencia a la frecuencia base absoluta de la canción
        private final Interval interval;
        private final String label; // Para saber si es Root u Overtone en los listados

        public HarmonicNode(String id, String parentId, Interval interval, String label) {
            this.id = id;
            this.parentId = parentId;
            this.interval = interval;
            this.label = label;
        }

        public String getId() { return id; }
        public String getParentId() { return parentId; }
        public Interval getInterval() { return interval; }
        public String getLabel() { return label; }
    }

    public static class Chord implements Serializable {
        private static final long serialVersionUID = 2L;
        private final String chordId;
        private final HarmonicNode rootNode;
        private final List<HarmonicNode> overtones = new ArrayList<>();
        private final Duration duration;

        public Chord(String chordId, HarmonicNode rootNode, Duration duration) {
            this.chordId = chordId;
            this.rootNode = rootNode;
            this.duration = duration;
        }

        public String getChordId() { return chordId; }
        public HarmonicNode getRootNode() { return rootNode; }
        public List<HarmonicNode> getOvertones() { return overtones; }
        public Duration getDuration() { return duration; }
    }

    private String title = "Relative Graph Score";
    private String author = "Graph Composer";
    private int bpm = 120;
    private double referenceFrequency = 440.0;

    private final List<Chord> chords = new ArrayList<>();
    private transient List<Runnable> listeners = new ArrayList<>();

    // Registra un acorde en el grafo armónico
    public synchronized void addChordInstance(Chord chord) {
        chords.add(chord);
        notifyListeners();
    }

    public synchronized void removeChordInstance(Chord chord) {
        if (chords.remove(chord)) {
            notifyListeners();
        }
    }

    public synchronized List<Chord> getChords() { return new ArrayList<>(chords); }

    // Busca un nodo dentro de cualquier acorde de la canción por su identificador exclusivo
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

    public synchronized int getNextChordIndex() { return chords.size() + 1; }

    /**
     * COMPUTATION ENGINE: RESOLVE GRAPH FREQUENCIES
     * Recorre las ramas recursivamente evaluando semitonos relativos en cascada.
     */
    public synchronized Map<String, Double> computeFrequencies() {
        Map<String, Double> calculated = new LinkedHashMap<>();

        // Primera pasada para tónicas y overtones
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

        // --- NEW MATHEMATICAL CALCULATION: DIRECT JUST RATIO MULTIPLICATION ---
        // Instead of Math.pow(2, semi/12), we multiply directly by the rational factor
        double freq = parentFreq * node.getInterval().getRatioValue();
        // ----------------------------------------------------------------------

        cache.put(node.getId(), freq);
    }

    // Getters / Setters estándar
    public synchronized String getTitle() { return title; }
    public synchronized void setTitle(String title) { this.title = title; notifyListeners(); }
    public synchronized String getAuthor() { return author; }
    public synchronized void setAuthor(String author) { this.author = author; notifyListeners(); }
    public synchronized int getBpm() { return bpm; }
    public synchronized void setBpm(int bpm) { this.bpm = bpm; notifyListeners(); }
    public synchronized double getReferenceFrequency() { return referenceFrequency; }
    public synchronized void setReferenceFrequency(double referenceFrequency) { this.referenceFrequency = referenceFrequency; notifyListeners(); }

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
        if (chords.isEmpty()) return "--- Empty Structural DAG Score ---";

        Map<String, Double> frequencies = computeFrequencies();
        StringBuilder sb = new StringBuilder("=== RELATIVE GRAPH NETWORK ARCHITECTURE ===\n");

        for (Chord c : chords) {
            HarmonicNode r = c.getRootNode();
            sb.append(String.format("Chord %s [%s]\n", c.getChordId(), c.getDuration()));
            sb.append(String.format("  └─ Tonic %s (Ref: %s, Int: %s) -> %.2f Hz\n",
              r.getId(), (r.getParentId() == null ? "BASE" : r.getParentId()), r.getInterval(), frequencies.getOrDefault(r.getId(), referenceFrequency)));

            for (HarmonicNode o : c.getOvertones()) {
                sb.append(String.format("      ├── Overtone %s (Ref: %s, Int: %s) -> %.2f Hz\n",
                  o.getId(), o.getParentId(), o.getInterval(), frequencies.getOrDefault(o.getId(), referenceFrequency)));
            }
        }
        return sb.toString();
    }
}
