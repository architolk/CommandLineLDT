# CommandLineLDT
An architectural example of creation an command line version of the Linked Data Theatre

Requirements:

- A sparql endpoint located at `http://localhost:8890/sparql`;
- If remote config is used: a named graph at `http://localhost:8080/stage` containing the configuration triples;
- All `elmo:Representation`s should have URI's starting with `http://localhost:8080/stage#`. 

Usage:

	java -cp "target/ldtcmd-1.0.0.jar;target/lib/*" nl.architolk.ldtcmd.CreatePage {$representation} {$local-or-remote}

The property `${representation}` is used to find the correct representation.

The property `${local-or-remote}` can have the values `local` or `remote`. The default value is `remote`. If `local` is used, the configuration is not loaded from the sparql endpoint, but from a file in the directory `configuration` with the name `${representation}.xml`.

The generated html file is placed into the `html` directory, with the name `${representation}.html`.

This repository also contains the files copied from the [Linked Data Theatre github repository](http://github.com/architolk/Linked-Data-Theatre). The follow files have been copied:

- All files in the `html` directory;
- All files in the `appearances` directory;
- All files in the `src/main/resources/xsl` directory, except `configuration.xsl` and `to-html.xsl`.
 