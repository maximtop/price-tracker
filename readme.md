# price-tracker

## build

We assume you have your machine in a good shape running recent versions of
java, maven, leiningen, etc.

  * extension is gets built into `resources/unpacked/compiled` folder.

    In one terminal session run (will build background and popup pages using figwheel):
    ```bash
    lein fig
    ```
    In a second terminal session run (will auto-build content-script):
    ```bash
    lein content
    ```
  * use latest Chrome Canary with [Custom Formatters](https://github.com/binaryage/cljs-devtools#enable-custom-formatters-in-your-chrome-canary) enabled
  * In Chrome Canary, open `chrome://extensions` and add `resources/unpacked` via "Load unpacked extension..."


