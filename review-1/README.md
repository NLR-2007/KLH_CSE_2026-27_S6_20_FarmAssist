# FarmAssist — Review 1 build

DSA-3 (25CS2103E) — Team 20
Nimma Lokesh Reddy (2520030366) · Ramagiri Rishik Rao (2520030333) · Manne Yashwanth Manoj (2520030369)

A deliberately small version of the project carrying **three algorithms only**:

| # | Algorithm | Where it runs |
|---|-----------|---------------|
| 1 | **Aho–Corasick** | one pass over the question finds every crop, symptom and disease named in it |
| 2 | **KMP** | checks each symptom exactly against every disease record, and locates a term inside an article so the snippet can be cut around it |
| 3 | **Rabin–Karp** | rolling-hash count of how often each term appears in each article — that count is the relevance score |

Nothing else from the full project is here. No spelling correction, no synonyms,
no Knapsack, no matching, no sorting algorithm — the ranking is a plain
insertion sort, marked as such in the code, because it is not one of the three.

---

## How to run

Double-click `run.bat`, or:

```
javac -encoding UTF-8 -d out (every .java under src)
java -Dstdout.encoding=UTF-8 -cp out app.Main data
```

Only a JDK is needed. Tested on JDK 25.

Try:

```
my tomato has brown spots and yellowing
rice has grey spots on the leaves
late blight in potato
how to grow wheat
```

Commands:

```
demo             runs all three algorithms on inputs small enough to check
trace on | off   the per algorithm trace line
color on | off   ANSI colour
ascii on | off   plain ASCII instead of box drawing characters
clear            clears the screen
help             the list above
exit             quits
```

The last two also work as command line switches, and `run.bat` passes anything
typed after it straight through:

```
run.bat --no-color      run.bat --ascii
```

---

## The console

Everything the program prints goes through `Ui.java`, which works out once what
the terminal can actually do and then keeps to it:

* **Colour** is used only when the output is a real terminal. Redirect the run
  into a file, or set `NO_COLOR`, and every escape disappears, so a saved
  transcript stays readable. `color off` does the same at any time, which is
  the switch to reach for if a very old console prints the escapes literally
  instead of obeying them.
* **Box drawing characters** are used only when the console is running in
  UTF-8. `run.bat` sets the code page to 65001 and starts Java with
  `-Dstdout.encoding=UTF-8`; if either does not take, `Ui` sees it in
  `stdout.encoding` and falls back to `+`, `-`, `|` and `#` rather than
  printing mojibake.
* **Nothing is measured with `String.length()`.** An escape sequence is several
  characters long and prints as none of them, so wrapping and padding both
  measure with `Ui.visibleLength`, and no line runs past 78 columns whether
  colour is on or off.

What that buys in the output itself:

* the question is echoed back with every Aho-Corasick hit painted in the colour
  of what it is, green crop, yellow symptom, red disease, so the automaton's
  work can be read straight off the sentence
* each result carries a bar showing its score against the best score in that
  list, and each section heading names the algorithm that ranked it
* in the article snippet, the term Rabin-Karp scored highest on, and KMP then
  located, is highlighted where it sits
* the trace lines stay dim and one to a line, so the shape of the pipeline is
  visible without drowning the answer

---

## Layout

```
FarmAssist-Review/
├── run.bat
├── data/
│   ├── keywords.txt      30 patterns for the Aho-Corasick automaton
│   ├── diseases.txt      8 diseases with symptoms and treatment
│   └── articles.txt      12 documents the search runs over
└── src/
    ├── algo/
    │   ├── KMP.java
    │   ├── RabinKarp.java
    │   └── AhoCorasick.java
    └── app/
        ├── Data.java     reads the three files, builds the automaton
        ├── Main.java     the question pipeline and what it shows
        └── Ui.java       colour, box drawing, wrapping, width
```

---

## Complexity

| Algorithm | Time | Space |
|---|---|---|
| KMP | O(n + m) | O(m) |
| Rabin–Karp | O(n + m) expected, O(n·m) worst case | O(1) |
| Aho–Corasick | O(n + M + z), M = total pattern length, z = matches | O(M) |

Rabin–Karp is only *expected* linear because equal hashes do not prove equal
strings — every hash hit is confirmed with a real character comparison in
`sameAt()`.

---

## Why the data files are `.txt` and not `.csv`

Both were considered. `.txt` won, for three reasons:

**1. The data already contains commas.** A symptom column reads
`brown spots, yellowing, stunted growth`. In a real CSV that field would have
to be wrapped in quotes, and the parser would then have to understand quoting,
escaped quotes and quoted newlines. With a `|` separator the parser stays one
line — `line.split("\\|")` — and the commas inside a field keep their natural
meaning as a list separator.

**2. Excel silently corrupts this data.** Double-clicking a `.csv` opens it in
Excel, and Excel reformats what it thinks it recognises. An NPK value like
`10-26-26` and a range like `400-600` are read as dates and written back
destroyed. Saving once is enough to ruin the file, and the damage is easy to
miss. A `.txt` file opens in Notepad and comes back exactly as it went in.

**3. The extension should not lie.** CSV means comma-separated. A file full of
`|` separators named `.csv` misleads anyone who opens it, and any tool that
tries to parse it properly will get it wrong.

Use `.csv` only if the data has to go into Excel or pandas *and* no field
contains a comma — neither is true here.

> Worth noting: the main project's `data/` folder has this exact mismatch —
> the files are named `.csv` but are pipe-separated. Renaming them to `.txt`
> would cost nothing and would stop Excel mangling the NPK and rainfall
> columns.

---

## Checked

The three algorithms were cross-checked against Java's own `String.indexOf`
over 4,000 randomly generated cases on a small alphabet, where overlapping
matches are common — `KMP.searchAll`, `KMP.search`, `RabinKarp.search` and
`RabinKarp.count` agree with it on every case. Edge cases (empty pattern,
pattern longer than text, pattern equal to text, overlapping `aaa` in `aaaa`)
and the Aho–Corasick word-boundary rule (`rice` must not match inside `price`,
`spot` must not match inside `spots`) were checked separately.
