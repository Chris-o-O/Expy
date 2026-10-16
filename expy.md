# Expy — notes de projet

PWA Android de budget personnel (propriétaire : Chris, Montréal ; échanges en français).
Dépôt : Chris-o-O/Expy, déployé sur GitHub Pages depuis `main`.

## Décisions de produit (ne pas remettre en cause sans demande)
- Android seulement, installable (Chrome › Installer l'application), hors ligne.
- Aucune synchronisation, aucun compte, aucun serveur. Les données restent sur le téléphone ; export CSV / sauvegarde JSON restaurable.
- Saisie manuelle (pas d'OCR). Objectif : ajouter une dépense en ~2 secondes.
- Clavier : celui de l'appareil par défaut (toucher le cadran) ; le pavé intégré n'apparaît qu'en repli ou si choisi dans Réglages.
- Partage de dépenses : local (pas d'utilisateurs). La part de l'utilisateur compte dans le budget ; solde par personne ; relevé envoyé par le partage Android.

## Architecture
- Un seul `index.html` (HTML + CSS + JS vanilla, aucune dépendance). Police : `fonts/bricolage.woff2`.
- `sw.js` : cache-first, mise à jour en un toucher (`SKIP_WAITING`). **À chaque version : changer `VERSION` dans `sw.js` ET le numéro affiché en bas de Réglages (« Expy · version X »).**
- `manifest.webmanifest` + `icons/`.
- Données : localStorage, clé `bex1:data` `{v:2, settings, months}` ; `bex1:theme` (thème, lu avant le rendu). Montants en cents ; dépenses classées par mois `YYYY-MM`.
- Dans `settings` : `periods`, `cats`, `debts`, `payments`, `debtBudget`, `wishes`, `goals`, `people`, `settles`, `theme`, `kb`, etc. `normalize()` migre les anciennes données.
- Une dépense partagée garde `amt` = la part de l'utilisateur (ce qui compte partout) et `split = {pid,total,pct,by}`.
- `window.__budget` expose des fonctions pour les tests.

## Écrans (barre flottante en bas)
Tableau de bord · Saisie (écran de lancement) · Dettes · Objectifs · Souhaits · Stats · Réglages.

## Règles de calcul
- Seuil par période `{start,end,amount}`, renouvelée automatiquement ; stats par mois civil ; seuils de catégorie mensuels (mois civil).
- Réserve (`reserveFor(p)`) : part du seuil mise de côté pour les dettes (case + date « libre d'ici », sinon budget mensuel de remboursement) et les objectifs d'épargne = restant × durée de la période ÷ jours jusqu'à l'échéance. Libre = seuil − réserve.
- Dettes : intérêts mensuels = solde × taux / 12 ; minimums puis surplus à la dette prioritaire (avalanche = taux le plus élevé, boule de neige = plus petit solde).
- Le reste sous le seuil (après réserve) alimente les dettes (option) puis les souhaits.

## Tests
Playwright (chromium) contre `python3 -m http.server 8770` dans le dossier ; ouvrir avec `serviceWorkers: 'block'`, viewport 390×844 mobile. Vérifier : aucune erreur console, thèmes clair/sombre, écran ≤ 800 px de haut, clavier simulé en réduisant la fenêtre.

## Publication
Commit sur `main` puis push ; GitHub Pages republie. Dire à l'utilisateur de toucher « Mettre à jour ». Ne jamais mettre de données personnelles dans le dépôt (public).

## Style (thème « C »)
Blanc/noir, formes arrondies, couleurs vives par catégorie, cartes pastel (dettes, objectifs, souhaits), cartes « héro » noires, tuiles de catégorie avec icônes seulement. Textes en français (Québec), ton direct.
