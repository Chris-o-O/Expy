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

## Écrans (barre ancrée en bas, v1.15 : maquette « C » choisie par Chris)
Tableau · Dettes · **Saisie** (bouton central surélevé, écran de lancement ; le toucher ouvre directement le clavier de l'appareil) · Objectifs (Épargne et Souhaits réunis, bascule en haut via `goalsSeg`) · Stats. Réglages (v1.18) : la pastille au prénom (`settings.name`, initiales via `avatar()`) en haut du Tableau de bord ouvre l'écran Réglages : colonne de catégories à gauche (`SET_SECS` : Profil, Périodes, Catégories, Apparence, Données, App ; `UI.setSec`), contenu à droite. Le prénom se saisit dans le profil (étape 1) ou dans Réglages › Profil. « Foresight » (`#v-future`, `#avenir`) s'ouvre par le bouton « Foresight » sous le récap « Mes comptes » du Tableau de bord (v1.16 : plus de carte d'aperçu).
- Mode clavier : bouton × (`#kbClose`) en haut à droite du cadran pour fermer le clavier sans passer par le bouton du clavier système (le montant est gardé).
- Saisie (v1.18.1) : plus de liste « Dernières dépenses » ; elles se consultent et se modifient dans Stats (dépenses du mois).
- Saisie : « Faisable à la maison », « Partagé », « Note, date » et le bouton ✓ n'apparaissent que pendant une saisie (classe `#v-home.typing` : clavier ouvert, montant tapé, partage ou options ouverts). Avec le pavé intégré, leur place reste réservée (visibility) pour que les touches ne bougent pas.
- Tableau de bord : Mes comptes (+ bouton Foresight), carte « Partages » (on te doit / tu dois, par personne → relevé).

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

## Game feel (v1.13)
- Réglage `settings.fx` (Réglages › Apparence › « Effets : animations et vibrations », classe `body.nofx`). `prefers-reduced-motion` coupe les animations, pas les vibrations.
- Vibrations (`buzz(kind)`) : tuile 8 ms, appui 12 ms, impact 22 ms × intensité `settings.haptic` (1 Légères ×1, 2 Moyennes ×2 par défaut, 3 Fortes ×3,5) ; bouton « Tester la vibration ». Sous ~15 ms, beaucoup de moteurs Android ne vibrent pas ; Chrome bloque toute vibration en mode silencieux. Cas réel (Pixel 9a de Chris) : rien ne vibrait car un réglage Android était à zéro (Paramètres › Son et vibration › Vibration et retour haptique) ; `navigator.vibrate` répondait pourtant `true`. Le bouton test (0,4 s + ligne « Diagnostic ») sert à isoler ce cas.
- Bouton Ajouter : écrasement à l'appui (scale .86), ressort au relâchement (`fabpop`) ; tuiles : écrasement.
- Après un ajout (`celebrateAdd`) : pièce couleur catégorie de la tuile à la tête de l'anneau, 460 ms ; à l'impact : vibration, rebond de l'anneau (`ringPop`), remplissage `easeOutBack` (dépassement ~8 %) via `UI.ringView`, « Reste » défile. Mode clavier (anneau caché) : la pièce vise la barre, qui rebondit et se remplit via `UI.barView` (classe `.hbar.live` coupe sa transition CSS) ; l'anneau reste figé à sa valeur d'avant et rattrape au retrait du clavier (`ringCatchUp` dans `endNative`). Rien ne bloque la saisie suivante.
- Anneau ↔ barre (v1.14, `morphDial`) : à l'ouverture du clavier, l'anneau se déroule comme un ruban depuis midi (partie posée droite, boucle restante qui rétrécit en roulant vers la droite) jusqu'à la barre ; inverse à la fermeture, puis `ringCatchUp`. Calque `#morphLayer` dessiné en coordonnées du cadran à chaque image (suit le redimensionnement), classe `.dial.morph` cache l'anneau et la barre réels ; segments partagés via `dialSegs()`. 520 ms, ease-in-out cubique.
- Projection (`projection()`, v1.15) : 12 mois. Compte de paie (`profile.payAcc`, sinon 1er chèque) += capacité réelle par paie (μ de `confidence()`, sinon paie − fixes − seuil) − épargne − paiements de dettes effectifs (`debtPath`, budget de remboursement, intérêts) ; compte d'épargne (`profile.saveAcc`, sinon 1er épargne ; vide = l'épargne reste dans le compte de paie) += part épargne ; autres comptes constants ; dettes négatives. Fourchette 80 % = ±1,28 σ √(paies) sur le compte de paie et la valeur nette. Écran « Foresight » : graphique Par compte / Valeur nette (`futureChart`) + tableau +1/+3/+6/+12 mois.
- Anneau → barre v1.15 : la barre est en HAUT du cadran en mode clavier ; le ruban garde la boucle centrée sur « Saisie » (phase 1, ruban tiré vers la gauche) puis la boucle roule vers la droite (phase 2).
- Prochaines étapes proposées : seuil franchi (tremblement léger + vibration double), confettis objectif atteint / dette soldée, bilan de fin de période façon « niveau terminé ».

## Tests
Playwright (chromium) contre `python3 -m http.server 8770` dans le dossier ; ouvrir avec `serviceWorkers: 'block'`, viewport 390×844 mobile. Vérifier : aucune erreur console, thèmes clair/sombre, écran ≤ 800 px de haut, clavier simulé en réduisant la fenêtre.

## Publication
Commit sur `main` puis push ; GitHub Pages republie. Dire à l'utilisateur de toucher « Mettre à jour ». Ne jamais mettre de données personnelles dans le dépôt (public).

## Style (thème « C »)
Blanc/noir, formes arrondies, couleurs vives par catégorie, cartes pastel (dettes, objectifs, souhaits), cartes « héro » noires, tuiles de catégorie avec icônes seulement. Textes en français (Québec), ton direct.
