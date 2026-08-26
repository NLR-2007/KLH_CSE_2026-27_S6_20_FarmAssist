package app;

import algo.AhoCorasick;
import algo.KMP;
import algo.RabinKarp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * FarmAssist - Review 1 build.
 *
 * A cut down version of the project that carries only three algorithms:
 *
 *   Aho-Corasick  finds every crop, symptom and disease named in the question,
 *                 in a single pass over the sentence
 *   KMP           checks each symptom against a disease record, exactly
 *   Rabin-Karp    counts how often a term appears in each article, which gives
 *                 the relevance score
 *
 * Nothing else from the full project is here on purpose.
 *
 * This class runs the pipeline and decides what to show; every character it
 * draws goes through Ui, which owns the colours, the box drawing and the
 * wrapping.
 */
public class Main {

    /** How many suspects and how many articles a single answer shows. */
    private static final int TOP_N = 3;

    private Data data;
    private boolean showTrace = true;

    public static void main(String[] args) {
        Ui.applyFlags(args);

        String folder = "data";
        for (String a : args) {
            if (!a.startsWith("--")) { folder = a; break; }
        }
        new Main().run(folder);
    }

    private void run(String folder) {
        Ui.banner("FARMASSIST  " + Ui.bullet() + "  Review 1",
                  "Aho-Corasick  " + Ui.bullet() + "  KMP  " + Ui.bullet() + "  Rabin-Karp",
                  "DSA-3 (25CS2103E)  " + Ui.bullet() + "  Team 20");

        data = new Data();
        data.loadAll(folder);

        if (data.articles.isEmpty()) {
            System.out.println();
            System.out.println("  " + Ui.paint("no data was loaded", Ui.DISEASE));
            System.out.println("  " + Ui.muted("run this from the FarmAssist-Review folder, or pass"));
            System.out.println("  " + Ui.muted("the data folder as an argument:  java -cp out app.Main data"));
            System.out.println();
            return;
        }

        System.out.println();
        System.out.println("  " + stat(data.articles.size(), "articles")
                + "   " + stat(data.diseases.size(), "diseases")
                + "   " + stat(data.keywords.patternCount(), "keyword patterns")
                + "   " + Ui.muted("loaded from " + folder + "/"));
        help();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.print(Ui.paint("  you " + Ui.chev() + " ", Ui.ACCENT));
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println();
                System.out.println("  " + Ui.muted("bye."));
                System.out.println();
                break;
            }
            if (line.equalsIgnoreCase("help"))  { help(); continue; }
            if (line.equalsIgnoreCase("demo"))  { demo(); continue; }
            if (line.equalsIgnoreCase("clear")) { Ui.clear(); continue; }

            if (line.equalsIgnoreCase("trace on"))   { showTrace = true;  setting("trace", "on");  continue; }
            if (line.equalsIgnoreCase("trace off"))  { showTrace = false; setting("trace", "off"); continue; }
            if (line.equalsIgnoreCase("color on")  || line.equalsIgnoreCase("colour on"))  { Ui.colour(true);  setting("colour", "on");  continue; }
            if (line.equalsIgnoreCase("color off") || line.equalsIgnoreCase("colour off")) { Ui.colour(false); setting("colour", "off"); continue; }
            if (line.equalsIgnoreCase("ascii on"))   { Ui.ascii(true);  setting("ascii", "on");  continue; }
            if (line.equalsIgnoreCase("ascii off"))  { Ui.ascii(false); setting("ascii", "off"); continue; }

            answer(line);
        }
        sc.close();
    }

    // ------------------------------------------------------------------
    //  the question pipeline
    // ------------------------------------------------------------------

    private void answer(String question) {

        // ---- 1. Aho-Corasick: what did the farmer actually mention? ----
        List<AhoCorasick.Match> hits = data.keywords.search(question);
        trace("Aho-Corasick", "one pass over the sentence, "
                + data.keywords.patternCount() + " patterns, "
                + hits.size() + (hits.size() == 1 ? " hit" : " hits"));

        Set<String> crops = new LinkedHashSet<>();
        Set<String> symptoms = new LinkedHashSet<>();
        Set<String> diseases = new LinkedHashSet<>();

        for (AhoCorasick.Match m : hits) {
            if (m.type.equals("CROP")) crops.add(m.word);
            else if (m.type.equals("SYMPTOM")) symptoms.add(m.word);
            else if (m.type.equals("DISEASE")) diseases.add(m.word);
        }

        if (!hits.isEmpty()) {
            Ui.section("READ", "Aho-Corasick, one pass over the sentence");
            System.out.println("    " + markUp(question, hits));
            System.out.println("    " + legend(crops, symptoms, diseases));
        }

        // ---- 2. KMP: does any disease record match those symptoms? ----
        if (!symptoms.isEmpty() || !diseases.isEmpty()) {
            diagnose(crops, symptoms, diseases);
        }

        // ---- 3. Rabin-Karp: rank the articles ----
        List<String> terms = new ArrayList<>();
        terms.addAll(crops);
        terms.addAll(diseases);
        terms.addAll(symptoms);
        if (terms.isEmpty()) {
            terms = plainWords(question);
            trace("term choice", "no keyword matched, falling back to plain words " + terms);
        }
        searchArticles(terms);
    }

    /**
     * Score every disease by how many of the symptoms KMP can find inside its
     * symptom text. A crop that matches is worth a couple of extra points.
     */
    private void diagnose(Set<String> crops, Set<String> symptoms, Set<String> named) {
        List<Suspect> ranked = new ArrayList<>();
        int kmpCalls = 0;

        for (Data.Disease d : data.diseases) {
            int score = 0;
            List<String> matched = new ArrayList<>();
            String haystack = d.symptomText();

            for (String s : symptoms) {
                kmpCalls++;
                if (KMP.contains(haystack, s.toLowerCase())) {
                    score += 3;
                    matched.add(s);
                }
            }
            // the farmer named the disease outright
            if (named.contains(d.name)) score += 5;

            boolean cropMatches = false;
            for (String c : crops) {
                if (d.crops.contains(c)) { cropMatches = true; break; }
            }
            if (cropMatches) score += 2;

            if (score > 0) ranked.add(new Suspect(d, score, matched, cropMatches));
        }

        trace("KMP", kmpCalls + " exact symptom searches over "
                + data.diseases.size() + " disease records, " + ranked.size() + " scored");

        if (ranked.isEmpty()) return;
        sortByScore(ranked);

        Ui.section("WHAT IT COULD BE", "scored on the symptoms KMP matched");
        int top = ranked.get(0).score;

        int shown = 0;
        for (Suspect d : ranked) {
            if (shown >= TOP_N) break;
            if (shown > 0) Ui.divider();
            shown++;

            System.out.println();
            Ui.card(shown, d.record.name.toUpperCase(), d.score, top);
            Ui.field("attacks", cropList(d.record.crops, crops), 5);
            Ui.field("symptoms", String.join(", ", d.record.symptoms), 5);
            if (!d.matched.isEmpty()) {
                Ui.field("matched", Ui.paint(String.join(", ", d.matched), Ui.SYMPTOM)
                        + Ui.muted("   " + Ui.back() + " KMP found these"), 5);
            }
            Ui.field("treat", d.record.treatment, 5);
        }
    }

    /**
     * Rabin-Karp counts the term hits in every article, and the counts become
     * the score. KMP is then used once on the winner to find where the best
     * term sits, so the snippet can be cut around it.
     */
    private void searchArticles(List<String> terms) {
        List<Hit> results = new ArrayList<>();
        int scans = 0;

        for (Data.Article a : data.articles) {
            String text = a.searchText();
            int score = 0;
            String best = null;
            int bestCount = 0;

            for (String term : terms) {
                scans++;
                int n = RabinKarp.count(text, term.toLowerCase());
                score += n;
                if (n > bestCount) { bestCount = n; best = term; }
            }
            if (score > 0) results.add(new Hit(a, score, best));
        }

        trace("Rabin-Karp", "rolling hash over " + data.articles.size()
                + " articles, " + scans + " scans, " + results.size() + " matched");

        if (results.isEmpty()) {
            Ui.section("ARTICLES", "ranked by Rabin-Karp term hits");
            System.out.println();
            System.out.println("    " + Ui.muted("nothing in the articles matches that."));
            return;
        }

        sortHits(results);
        Ui.section("ARTICLES", "ranked by Rabin-Karp term hits");
        int top = results.get(0).score;

        int shown = 0;
        for (Hit h : results) {
            if (shown >= TOP_N) break;
            // work the snippet out first, so its trace line does not land in
            // the middle of the block we are about to print
            String text = snippet(h.article, h.bestTerm);
            if (shown > 0) Ui.divider();
            shown++;

            System.out.println();
            Ui.card(shown, h.article.title, h.score, top);
            System.out.println("     " + Ui.muted(h.article.id + "  " + Ui.bullet()
                    + "  best term ") + Ui.paint(h.bestTerm, Ui.SYMPTOM)
                    + Ui.muted("  " + Ui.bullet() + "  " + h.score
                    + (h.score == 1 ? " hit" : " hits") + " in this article"));
            System.out.println("     " + Ui.highlight(Ui.wrap(text, 5), h.bestTerm, Ui.MARK));
        }
    }

    /** KMP locates the term, then we cut a window of text around it. */
    private String snippet(Data.Article a, String term) {
        String body = a.body;
        int at = KMP.search(body.toLowerCase(), term.toLowerCase());

        // a term can score off the title alone and then simply not be in the
        // body, so show the opening of the article rather than nothing
        if (at < 0) {
            trace("KMP", "\"" + term + "\" is in the title of " + a.id
                    + ", not the body - showing the opening");
            return Ui.shorten(body, 150);
        }
        trace("KMP", "located \"" + term + "\" in " + a.id + " at position " + at);

        int from = Math.max(0, at - 45);
        int to = Math.min(body.length(), at + 105);
        String cut = body.substring(from, to).trim();
        if (from > 0) cut = "... " + cut;
        if (to < body.length()) cut = cut + " ...";
        return cut;
    }

    // ------------------------------------------------------------------
    //  the demo the reviewer asks for
    // ------------------------------------------------------------------

    private void demo() {
        Ui.banner("THE THREE ALGORITHMS ON SMALL INPUTS",
                  "each one run on an input small enough to check by eye");

        Ui.section("1  KMP" + Ui.spaces(10) + "exact search, O(n + m)");
        String text = "the blast disease attacks rice and blast spreads fast";
        String pat = "blast";
        System.out.println();
        Ui.field("text", Ui.highlight(text, pat, Ui.MARK), 5);
        Ui.field("pattern", Ui.paint(pat, Ui.SYMPTOM), 5);
        Ui.field("LPS", java.util.Arrays.toString(KMP.buildLPS(pat))
                + Ui.muted("   " + Ui.back() + " the jump table"), 5);
        Ui.field("first at", String.valueOf(KMP.search(text, pat)), 5);
        Ui.field("all at", String.valueOf(KMP.searchAll(text, pat)), 5);

        Ui.section("2  Rabin-Karp" + Ui.spaces(3) + "rolling hash, O(n + m) expected");
        String t2 = "urea and dap and urea again with urea";
        System.out.println();
        Ui.field("text", Ui.highlight(t2, "urea", Ui.MARK), 5);
        Ui.field("pattern", Ui.paint("urea", Ui.SYMPTOM), 5);
        Ui.field("found at", String.valueOf(RabinKarp.search(t2, "urea")), 5);
        Ui.field("count", RabinKarp.count(t2, "urea")
                + Ui.muted("   " + Ui.back() + " this count is the relevance score"), 5);

        Ui.section("3  Aho-Corasick" + Ui.spaces(1) + "all patterns in one pass, O(n + z)");
        AhoCorasick ac = new AhoCorasick();
        ac.addPattern("tomato", "CROP");
        ac.addPattern("rice", "CROP");
        ac.addPattern("yellow", "SYMPTOM");
        ac.addPattern("spot", "SYMPTOM");
        ac.build();

        String q = "my tomato has yellow leaves and brown spots, the price of rice is high";
        List<AhoCorasick.Match> found = ac.search(q);

        System.out.println();
        Ui.field("patterns", Ui.paint("tomato", Ui.CROP) + ", " + Ui.paint("rice", Ui.CROP)
                + ", " + Ui.paint("yellow", Ui.SYMPTOM) + ", " + Ui.paint("spot", Ui.SYMPTOM), 5);
        Ui.field("query", markUp(q, found), 5);

        StringBuilder matches = new StringBuilder();
        for (AhoCorasick.Match m : found) {
            if (matches.length() > 0) matches.append("  ");
            matches.append(Ui.paint(m.toString(), Ui.typeColour(m.type)));
        }
        Ui.field("matches", matches.toString(), 5);
        Ui.field("note", Ui.muted("\"rice\" inside \"price\" is not reported, and \"spot\" "
                + "inside \"spots\" is not either - the automaton checks the word boundary"), 5);
        System.out.println();
    }

    // ------------------------------------------------------------------
    //  turning results into something readable
    // ------------------------------------------------------------------

    /** The question with every Aho-Corasick hit painted in its own colour. */
    private String markUp(String question, List<AhoCorasick.Match> hits) {
        List<AhoCorasick.Match> ordered = new ArrayList<>(hits);
        ordered.sort(Comparator
                .comparingInt((AhoCorasick.Match m) -> m.position)
                .thenComparing(m -> -m.word.length()));

        StringBuilder out = new StringBuilder();
        int at = 0;
        for (AhoCorasick.Match m : ordered) {
            if (m.position < at) continue;              // an overlap, already painted
            int end = m.position + m.word.length();
            if (end > question.length()) continue;
            out.append(question, at, m.position)
               .append(Ui.paint(question.substring(m.position, end), Ui.typeColour(m.type)));
            at = end;
        }
        return out.append(question.substring(at)).toString();
    }

    /** The coloured key under the question: what each colour meant. */
    private String legend(Set<String> crops, Set<String> symptoms, Set<String> diseases) {
        StringBuilder out = new StringBuilder();
        appendGroup(out, "crop", crops, Ui.CROP);
        appendGroup(out, "symptom", symptoms, Ui.SYMPTOM);
        appendGroup(out, "disease", diseases, Ui.DISEASE);
        return out.length() == 0 ? Ui.muted("nothing recognised") : out.toString();
    }

    private void appendGroup(StringBuilder out, String label, Set<String> values, String colour) {
        if (values.isEmpty()) return;
        if (out.length() > 0) out.append("   ");
        out.append(Ui.paint(Ui.dot(), colour))
           .append(Ui.muted(" " + label + " "))
           .append(String.join(", ", values));
    }

    /** The crops a disease attacks, with the farmer's own crop pointed out. */
    private String cropList(List<String> attacks, Set<String> mine) {
        StringBuilder out = new StringBuilder();
        String hit = null;
        for (String c : attacks) {
            if (out.length() > 0) out.append(", ");
            if (mine.contains(c)) {
                out.append(Ui.paint(c, Ui.CROP));
                hit = c;
            } else {
                out.append(c);
            }
        }
        if (hit != null) out.append(Ui.muted("   " + Ui.back() + " your crop"));
        return out.toString();
    }

    private String stat(int n, String label) {
        return Ui.paint(String.valueOf(n), Ui.BOLD) + " " + Ui.muted(label);
    }

    private void setting(String name, String value) {
        System.out.println("  " + Ui.muted(name + " " + Ui.chev() + " ") + Ui.accent(value));
    }

    private void trace(String algorithm, String message) {
        if (!showTrace) return;
        System.out.println("   " + Ui.muted(Ui.bullet() + " ")
                + Ui.paint(Ui.pad(algorithm, 14), Ui.ACCENT)
                + Ui.muted(Ui.wrap(message, 19)));
    }

    // ------------------------------------------------------------------
    //  small helpers
    // ------------------------------------------------------------------

    /** A disease that scored something, waiting to be ranked. */
    private static class Suspect {
        final Data.Disease record;
        final int score;
        final List<String> matched;
        final boolean cropMatches;

        Suspect(Data.Disease record, int score, List<String> matched, boolean cropMatches) {
            this.record = record;
            this.score = score;
            this.matched = matched;
            this.cropMatches = cropMatches;
        }
    }

    /** An article that scored something. */
    private static class Hit {
        final Data.Article article;
        final int score;
        final String bestTerm;

        Hit(Data.Article article, int score, String bestTerm) {
            this.article = article;
            this.score = score;
            this.bestTerm = bestTerm;
        }
    }

    // Only three algorithms are in scope for this build, so the ordering below
    // is a plain insertion sort. It is here to put results in order, it is not
    // one of the three being demonstrated.

    private void sortByScore(List<Suspect> list) {
        for (int i = 1; i < list.size(); i++) {
            Suspect key = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).score < key.score) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private void sortHits(List<Hit> list) {
        for (int i = 1; i < list.size(); i++) {
            Hit key = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).score < key.score) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    /** Words worth searching for when no keyword was recognised. */
    private List<String> plainWords(String question) {
        List<String> out = new ArrayList<>();
        for (String w : question.toLowerCase().split("[^a-z0-9]+")) {
            if (w.length() >= 4 && !isCommon(w)) out.add(w);
        }
        return out;
    }

    private boolean isCommon(String w) {
        String[] skip = {"what", "when", "which", "where", "how", "does", "the", "this",
                         "that", "with", "from", "have", "should", "about", "tell",
                         "please", "there", "they", "will", "your", "leaves"};
        for (String s : skip) {
            if (s.equals(w)) return true;
        }
        return false;
    }

    private void help() {
        Ui.section("TRY");
        String[] examples = {
            "my tomato has brown spots and yellowing",
            "rice has grey spots on the leaves",
            "late blight in potato",
            "how to grow wheat",
        };
        System.out.println();
        for (String e : examples) {
            System.out.println("    " + Ui.muted(Ui.arrow() + " ") + e);
        }

        Ui.section("COMMANDS");
        System.out.println();
        command("demo", "run all three algorithms on inputs small enough to check");
        command("trace on | off", "show or hide the algorithm trace");
        command("color on | off", "ANSI colour");
        command("ascii on | off", "plain ASCII instead of box drawing characters");
        command("clear", "clear the screen");
        command("help", "this list");
        command("exit", "quit");
    }

    private void command(String name, String what) {
        System.out.println("    " + Ui.paint(Ui.pad(name, 18), Ui.ACCENT) + Ui.muted(what));
    }
}
