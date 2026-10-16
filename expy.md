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
- Réserve (`reserveFor(p)`) : part du seuil mise de côté pour les dettes (case « Prendre ce budget dans mon seuil » → budget mensuel de remboursement au prorata) et les objectifs d'épargne = restant × durée de la période ÷ jours jusqu'à l'échéance. Libre = seuil − réserve.
- Dettes et objectifs sont séparés : tout ce qui touche les dettes (budget, date « libre d'ici » → `debtNeedFor()` donne le budget nécessaire, intérêts compris) vit dans l'onglet Dettes ; Objectifs = épargne seulement.
- Profil (`settings.profile`, v1.10) : paie nette (variable → montant prudent), fréquence (`PAY_FREQ`), charges fixes (versement au compte commun par paie si `common`, plus liste des charges payées seul en $/mois ; les deux s'additionnent, v1.12), épargne et dettes par paie. Dépenses courantes = paie − fixes − épargne − dettes → seuil des périodes `auto`, alignées sur les paies (`payPeriodAt`). Seuil modifiable à la main ; la période suivante reprend le montant de la précédente. Avec un profil : budget de remboursement = part dettes, hors seuil ; les objectifs sont financés par la part épargne (pas retirés du seuil).
- Repère d'épargne (`saveReco`) : 10 % (<30 ans), 15 % (30-44), 20 % (45-54), 25 % (55+) ; −5 pts sous 40 000 $ net/an, +5 au-dessus de 90 000 $ ; min 5 % ; relevé si les objectifs demandent plus. C'est un repère, pas un conseil.
- Comptes (`settings.accounts`) : photo des soldes (chèque/épargne), valeur nette au tableau de bord. Pas de suivi des virements.
- Profil proposé au 1er lancement (« Passer »), bandeau pour les utilisateurs existants (« Plus tard » → `profileSkip`), refaisable dans Réglages.
- Indice de confiance (`confidence()`, v1.11, profil requis) : périodes terminées des 120 derniers jours, ramenées à une paie ; capacité = paie reçue (`period.paid`, saisie au bilan de fin de période, sinon paie du profil) − fixes − dépensé. Moyenne μ, écart-type σ (plancher 8 % de la paie, 15 % si variable, gonflé si peu de périodes). Objectifs et date « libre de dettes » servis par échéance ; besoin cumulé (sans date de dettes, la part dettes passe d'abord) ; P = Φ((μ − cumul) / (σ/√N paies)). ≥ 80 % Atteignable, ≥ 50 % Serré, sinon Peu probable. Explication : écart moyen au seuil, paie réelle sous le profil ; « Limité par X » quand un élément atteignable seul (≥ 50 %) est bloqué par une échéance plus proche.
- Dettes : intérêts mensuels = solde × taux / 12 ; minimums puis surplus à la dette prioritaire (avalanche = taux le plus élevé, boule de neige = plus petit solde).
- Le reste sous le seuil (après réserve) alimente les dettes (option) puis les souhaits.

## Tests
Playwright (chromium) contre `python3 -m http.server 8770` dans le dossier ; ouvrir avec `serviceWorkers: 'block'`, viewport 390×844 mobile. Vérifier : aucune erreur console, thèmes clair/sombre, écran ≤ 800 px de haut, clavier simulé en réduisant la fenêtre.

## Publication
Commit sur `main` puis push ; GitHub Pages republie. Dire à l'utilisateur de toucher « Mettre à jour ». Ne jamais mettre de données personnelles dans le dépôt (public).

## Style (thème « C »)
Blanc/noir, formes arrondies, couleurs vives par catégorie, cartes pastel (dettes, objectifs, souhaits), cartes « héro » noires, tuiles de catégorie avec icônes seulement. Textes en français (Québec), ton direct.
