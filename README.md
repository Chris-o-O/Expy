# Expy

Application web installable (PWA) pour saisir une dépense en 2 secondes depuis l'écran d'accueil Android.

- Saisie au pavé intégré : montant, catégorie, Ajouter.
- Seuil par période choisie (début et fin), renouvelée automatiquement ; stats par mois civil.
- Indicateur « faisable à la maison ».
- Plusieurs dettes, méthode avalanche ou boule de neige, date de libération complète.
- Fonctionne hors ligne. Les données restent sur le téléphone (localStorage, stockage persistant) ; export CSV et sauvegarde JSON restaurable.

Aucune donnée personnelle n'est stockée dans ce dépôt.

## Installer
Ouvrir le site GitHub Pages dans Chrome sur Android, puis menu ⋮ › Installer l'application.

## Mettre à jour
Modifier les fichiers, puis changer `VERSION` dans `sw.js` pour que les téléphones prennent la nouvelle version au lancement suivant.
