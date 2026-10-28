# quillmark-modern

> **SYNTHETIC.** Quillmark Trust & Savings is a fictional bank invented for the [Diocle](https://github.com/Nivetha200111/Diocle) hackathon project. Nothing here is derived from any real system, employer, client or person.

The Java side of the Quillmark modernization. [Diocle](https://github.com/Nivetha200111/Diocle) converts programs from [quillmark-legacy](https://github.com/Nivetha200111/quillmark-legacy) and delivers them here as pull requests, one folder per program:

```
programs/<PROGRAM>/
  java/              converted Java (Meta Muse Spark, repaired by Diocle's agent)
  sample.json        record layouts and DD names, derived from the legacy copybooks
  certificate.json   equivalence certificate: pinned legacy commit, pinned verifier, held-out results, agent cost
```

## Don't trust the certificate: CI checks it

Every pull request runs **reverify** (`.github/workflows/reverify.yml`). It builds GnuCOBOL 3.2, installs the Diocle verifier at the commit the certificate names, fetches the COBOL at the legacy commit the certificate names, and runs the original and the Java on 1,000 fresh inputs from a seed tied to the CI run, a seed Diocle never used. Any field that differs fails the check.
