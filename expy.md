# Expy — notes de projet

PWA Android de budget personnel (propriétaire : Chris, Montréal ; échanges en français).
Dépôt : Chris-o-O/Expy, déployé sur GitHub Pages depuis `main`.

## Décisions de produit (ne pas remettre en cause sans demande)
- Android seulement, hors ligne. Deux formes du même code : **app Android** (Capacitor, APK installé directement, pas de Play Store — choix de Chris, v1.22) et PWA (GitHub Pages) pendant la transition.
- Aucune synchronisation, aucun compte, aucun serveur. Les données restent sur le téléphone ; export CSV / sauvegarde JSON restaurable.
- Saisie manuelle (pas d'OCR). Objectif : ajouter une dépense en ~2 secondes.
- Clavier : celui de l'appareil par défaut (toucher le cadran) ; le pavé intégré n'apparaît qu'en repli ou si choisi dans Réglages.
- Partage de dépenses : local (pas d'utilisateurs). La part de l'utilisateur compte dans le budget ; solde par personne ; relevé envoyé par le partage Android.

## Architecture
- Un seul `index.html` (HTML + CSS + JS vanilla, aucune dépendance). Police : `fonts/bricolage.woff2`.
- `sw.js` : cache-first, mise à jour en un toucher (`SKIP_WAITING`). **À chaque version : changer `VERSION` dans `sw.js` ET le numéro affiché en bas de Réglages (« Expy · version X »).**
- `manifest.webmanifest` + `icons/`. Icône (v1.19, piste « A · la pièce qui roule » choisie par Chris) : fond bleu #2B47D9, pièce or #F5A300 qui roule vers la droite en déroulant un ruban noir, traits de vitesse blancs. `icon-192/512` = carré arrondi (rx 22 %), `icon-maskable-512` = plein cadre (dessin dans la zone sûre de 80 %). Les PNG sont générés depuis le SVG (viewBox 100) avec Chromium.
- Données : localStorage, clé `bex1:data` `{v:2, settings, months}` ; `bex1:theme` (thème, lu avant le rendu). Montants en cents ; dépenses classées par mois `YYYY-MM`.
- Dans `settings` : `periods`, `cats`, `debts`, `payments`, `debtBudget`, `wishes`, `goals`, `people`, `settles`, `theme`, `kb`, etc. `normalize()` migre les anciennes données.
- Une dépense partagée garde `amt` = la part de l'utilisateur (ce qui compte partout) et `split = {pid,total,pct,by}`.
- `window.__budget` expose des fonctions pour les tests.
- Sécurité (v1.24.1) : tout texte venant des données passe par `esc()` avant d'aller dans du HTML (notifications de paiement comprises). Une sauvegarde restaurée peut venir de n'importe où : `normalize()` + `cleanMonths()` ne gardent que des valeurs bien formées (nombres, dates ISO, couleurs `#RRGGBB`, ids en texte), aussi au chargement du cache et avant d'afficher la fenêtre « Restaurer ».

## App Android (Capacitor, v1.22)
- `index.html` reste la seule source. `npm run sync` = `scripts/build-www.mjs` (copie l'app dans `www/` + ajoute `capacitor.js`, seulement dans cette copie) puis `npx cap sync android`. Projet natif dans `android/` (appId `io.github.chrisoo.expy`).
- Dans le code : `NATIVE` (Capacitor présent) et `NP` (plugins Filesystem, Share, Haptics, App). En app : export = fichier en cache + partage Android ; relevé = partage Android ; vibrations = Haptics ; pas de service worker ; bouton retour Android = ferme la feuille / le clavier, puis revient à Saisie, puis met l'app en arrière-plan.
- Version : lue dans `sw.js` (`expy-vX.Y.Z`) par `android/app/build.gradle` (versionCode = X×10000 + Y×100 + Z). Donc toujours : changer `VERSION` dans `sw.js` + le numéro affiché dans Réglages.
- APK : `.github/workflows/android.yml` (au push sur `main` quand `sw.js` change, ou à la main) compile, signe et publie une Release GitHub `vX.Y.Z` avec `expy-X.Y.Z.apk`. Signature : secrets `EXPY_KEYSTORE_B64` (keystore PKCS12 en base64, alias `expy`) et `EXPY_KEYSTORE_PASS`. La clé n'est JAMAIS dans le dépôt ; Chris en garde une copie (sans elle, plus de mise à jour possible sans désinstaller).
- Icônes Android (mipmap classiques, rondes, adaptatives) et écrans de démarrage générés depuis le SVG de la pièce, fond #2B47D9.
- Pas de SDK Android dans l'environnement de Claude (téléchargements Google bloqués) : la compilation se vérifie dans GitHub Actions.
- Dépenses détectées (v1.24) : code natif Java dans `android/app/src/main/java/io/github/chrisoo/expy/` :
  - `PayParser` (Java pur, autotest : `java PayParser.java`) : montant (formats canadiens) + commerçant ; Google Wallet accepté sans mot-clé, autres apps : mot de paiement requis ; dépôts, soldes, remboursements, refus, échéances écartés ; > 50 000 $ ignoré.
  - `PayListener` (`NotificationListenerService`, accès accordé par l'utilisateur) → `PayStore` (SharedPreferences, 50 max, doublon = même app + même montant à moins de 3 min) + notification Expy « Dépense détectée » (canal `expy_pay`, icône `ic_stat_coin`).
  - `PayNotifPlugin` (« PayNotif », enregistré dans `MainActivity`) : `status`, `requestNotify`, `openAccessSettings`, `openAppSettings` (paramètres restreints Android 13+ pour un APK hors Play Store), `setOn`, `pending`, `dismiss`, `simulate` ; évènement `detected`.
  - Côté web : bandeau « N dépenses détectées » → feuille (`sheetDetected`) : catégorie devinée (`detGuessCat` : mémoire `settings.merchantCats`, nom de catégorie, `MERCHANT_HINTS`), Ajouter (dépense `src:'notif'`, portée sur la carte comme une saisie) ou Ignorer. Réglages › App : carte « Dépenses détectées » (étapes, interrupteur, exemple).
- Animation anneau → barre réglée par Chris dans le labo : Douce · 920 ms · 85 % autour de « Saisie » (`MORPH = {ms: 920, a: .85, b: .68}`, `--mo-ms: 920ms`).

## Écrans (barre ancrée en bas, v1.15 : maquette « C » choisie par Chris ; icônes seules depuis v1.20, libellés en aria-label)
Tableau · Dettes · **Saisie** (bouton central surélevé, écran de lancement ; le toucher ouvre directement le clavier de l'appareil) · Objectifs (Épargne et Souhaits réunis, bascule en haut via `goalsSeg`) · Stats. Réglages (v1.18) : la pastille au prénom (`settings.name`, initiales via `avatar()`) en haut du Tableau de bord ouvre l'écran Réglages : colonne de catégories à gauche (`SET_SECS` : Profil, Périodes, Catégories, Apparence, Données, App ; `UI.setSec`), contenu à droite. Le prénom se saisit dans le profil (étape 1) ou dans Réglages › Profil. « Foresight » (`#v-future`, `#avenir`) s'ouvre par le bouton « Foresight » sous le récap « Mes comptes » du Tableau de bord (v1.16 : plus de carte d'aperçu).
- Mode clavier : bouton × (`#kbClose`) en haut à droite du cadran pour fermer le clavier sans passer par le bouton du clavier système (le montant est gardé).
- Saisie (v1.18.1) : plus de liste « Dernières dépenses » ; elles se consultent et se modifient dans Stats (dépenses du mois).
- Saisie : « Faisable à la maison », « Partagé », « Note, date » et le bouton ✓ n'apparaissent que pendant une saisie (classe `#v-home.typing` : clavier ouvert, montant tapé, partage ou options ouverts). Avec le pavé intégré, leur place reste réservée (visibility) pour que les touches ne bougent pas.
- Entrée d'argent (v1.21) : bascule « Entrée d'argent » pendant la saisie (`UI.income`, classe `#v-home.inc` : montant vert, catégories masquées, note « De qui ? »). `settings.incomes` = `{id, amt, ts, note, acc}` : ajoutée au compte de paie et au « Reste » de la période (`homeState().inc`) ; liste et suppression dans Stats.
- Catégories (v1.21) : pastille au nom sous la tuile choisie (`data-name`, `::after`) ; appui long (280 ms) sur une tuile puis glisser = bulle avec le nom de la tuile sous le doigt + petit tic, relâcher choisit (`tileScrub`, événements tactiles ; un glissement rapide fait toujours défiler).
- Repère de rythme (trait bleu sur l'anneau / la barre) = `homeState().pace` = libre × (jour − 0,5) ÷ durée. v1.23 : « Rythme X $ » dans la légende sous le cadran ; appui long (450 ms) sur le cadran = bulle explicative (avance ou retard sur le rythme) ; un toucher simple ouvre toujours le clavier (`UI.paceShown` bloque l'ouverture juste après l'appui long).
- Tableau de bord : Mes comptes (+ bouton Foresight), carte « Partages » (on te doit / tu dois, par personne → relevé).

## Règles de calcul
- Seuil par période `{start,end,amount}`, renouvelée automatiquement ; stats par mois civil ; seuils de catégorie mensuels (mois civil).
- Réserve (`reserveFor(p)`) : part du seuil mise de côté pour les dettes (case « Prendre ce budget dans mon seuil » → budget mensuel de remboursement au prorata) et les objectifs d'épargne = restant × durée de la période ÷ jours jusqu'à l'échéance. Libre = seuil − réserve.
- Dettes et objectifs sont séparés : tout ce qui touche les dettes (budget, date « libre d'ici » → `debtNeedFor()` donne le budget nécessaire, intérêts compris) vit dans l'onglet Dettes ; Objectifs = épargne seulement.
- Profil (`settings.profile`, v1.10) : paie nette (variable → montant prudent), fréquence (`PAY_FREQ`), charges fixes (versement au compte commun par paie si `common`, plus liste des charges payées seul en $/mois ; les deux s'additionnent, v1.12), épargne et dettes par paie. Dépenses courantes = paie − fixes − épargne − dettes → seuil des périodes `auto`, alignées sur les paies (`payPeriodAt`). Seuil modifiable à la main ; la période suivante reprend le montant de la précédente. Avec un profil : budget de remboursement = part dettes, hors seuil ; les objectifs sont financés par la part épargne (pas retirés du seuil).
- Repère d'épargne (`saveReco`) : 10 % (<30 ans), 15 % (30-44), 20 % (45-54), 25 % (55+) ; −5 pts sous 40 000 $ net/an, +5 au-dessus de 90 000 $ ; min 5 % ; relevé si les objectifs demandent plus. C'est un repère, pas un conseil.
- Comptes (`settings.accounts`) : soldes confirmés (`updated` = horodatage ISO) + mouvements suivis par Expy (v1.20) :
  - Dépenses → carte ou compte qui les paie (`settings.spendFrom` : id de dette ou de compte ; '' = ne pas suivre ; absent = 1re carte). Chaque dépense garde `it.via` ; `charge(it, ±1)` ajuste le solde à l'ajout, l'annulation, la modification et la suppression. Montant porté = part de l'utilisateur, ou total s'il a payé une dépense partagée (`chargeOf`). Une dépense datée avant la dernière confirmation du solde n'est pas portée (`tagSource`).
  - « Payer » une carte (tableau de bord, `sheetCardPay`) : carte −, compte choisi −, paiement enregistré.
  - Bilan de fin de période : case « Ajouter la paie à {compte de paie} » (cochée si ce compte a été confirmé avant le début de la période) → compte de paie += paie reçue − charges fixes − épargne ; compte d'épargne += épargne ; `period.deposited` évite le double dépôt.
  - Tableau de bord : chaque carte affichée à part avec « dont X $ de dépenses saisies », valeur nette en direct.
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
- Anneau → barre v1.18.2 : une seule horloge — les transitions CSS du cadran (taille, texte) utilisent `--mo-ms` / `--mo-ease` (520 ms, ease-in-out cubique) et le ruban `MORPH` (mêmes valeurs) ; bascule entre phases par smoothsteps qui se chevauchent (`MORPH.a` = fin phase 1, `MORPH.b` = début phase 2) ; le SVG du ruban est créé une fois et seuls les `d` changent à chaque image. Labo de réglage jouable sur la page de maquettes (artboard « Labo du ruban »).
- Anneau → barre v1.15 : la barre est en HAUT du cadran en mode clavier ; le ruban garde la boucle centrée sur « Saisie » (phase 1, ruban tiré vers la gauche) puis la boucle roule vers la droite (phase 2).
- Prochaines étapes proposées : seuil franchi (tremblement léger + vibration double), confettis objectif atteint / dette soldée, bilan de fin de période façon « niveau terminé ».

## Tests
Playwright (chromium) contre `python3 -m http.server 8770` dans le dossier ; ouvrir avec `serviceWorkers: 'block'`, viewport 390×844 mobile. Vérifier : aucune erreur console, thèmes clair/sombre, écran ≤ 800 px de haut, clavier simulé en réduisant la fenêtre.

## Publication
Commit sur `main` puis push ; GitHub Pages republie. Dire à l'utilisateur de toucher « Mettre à jour ». Ne jamais mettre de données personnelles dans le dépôt (public).

## Style (thème « C »)
Blanc/noir, formes arrondies, couleurs vives par catégorie, cartes pastel (dettes, objectifs, souhaits), cartes « héro » noires, tuiles de catégorie avec icônes seulement. Textes en français (Québec), ton direct.
