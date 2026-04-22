# Phoenix Down - AI Working Guide

Ce document sert de référence rapide pour l'agent IA sur ce repository: ce qui est compris, ce qui reste à faire, et la manière attendue de l'exécuter.

## 1) Ce qui est compris (état actuel)

### Objectif produit
- Unifier les flux réseau (SMB / SFTP / WebDAV) sans ambiguïté de nommage.
- Garantir un comportement cohérent des sauvegardes entre sources réseau.
- Préserver la priorité de la source locale en cas de doublons de jeux.
- Garder l'UX claire: badge de source visible, logs accessibles, formulaires fluides.

### Décisions validées
- Pas de legacy/fallback de compatibilité si non demandé explicitement (mode dev actif).
- Les chemins de sauvegarde doivent être homogènes entre backends.
- Le scan WebDAV doit fonctionner via parsing XML robuste (namespace-aware).
- Le scan SFTP peut fonctionner techniquement, mais n'est pas forcément adapté au scan massif produit.

### Correctifs déjà appliqués
- Correction des chemins de sauvegarde SMB/SFTP/WebDAV pour éviter les divergences de layout.
- Ajout d'un backend de sauvegarde WebDAV côté shared (`WebDavSavesStorage`).
- Résolveur de sauvegarde branché pour SMB/SFTP/WebDAV.
- Parser WebDAV corrigé dans le listing (`NetworkClient`) pour XML namespacé.
- Badge de source rétabli sur les cartes jeux (SMB/SFTP/WebDAV/local).
- Priorité locale maintenue dans la déduplication des jeux.
- Entrée des logs déplacée dans Paramètres avancés.
- Boutons du viewer de logs alignés avec le thème Material.
- Navigation clavier des formulaires (Enter -> champ suivant) ajoutée.

## 2) Ce que l'agent doit faire ensuite

### Priorité immédiate
1. Stabiliser le scan WebDAV sur différents serveurs (Nginx WebDAV, NAS, etc.).
2. Vérifier/ajuster l'ordre de tri des jeux scan SFTP (actuellement perçu comme désordonné).
3. Tester les sauvegardes multi-source complètes: WebDAV + SMB + SFTP.
4. Valider les téléchargements après refactor réseau.

### Validation fonctionnelle attendue
- Mobile:
  - Scan, badge source, téléchargement, sauvegarde/restauration.
- TV:
  - Régression globale (settings, scan, lancement, navigation).
- Déduplication:
  - En doublon, la source locale reste prioritaire.

## 3) Comment l'agent doit le faire (méthode)

### Règles de travail
- Faire des changements minimaux et ciblés.
- Éviter les refactors larges hors scope de la demande.
- Compiler après chaque lot significatif de modifications.
- En cas d'échec build: extraire et corriger la première erreur bloquante, puis rebuild.

### Stratégie de debug réseau
1. Reproduire localement avec logs ciblés.
2. Vérifier d'abord la donnée brute (réponse `PROPFIND`, `href`, `resourcetype`, codes HTTP).
3. Vérifier ensuite le mapping interne (`path`, `relativePath`, filtre extensions, tri).
4. Corriger côté parser/normalisation avant d'introduire des contournements.

### Convention de qualité
- Si une feature touche plusieurs protocoles, vérifier SMB + SFTP + WebDAV.
- Si une feature touche la bibliothèque, vérifier aussi les règles de déduplication.
- Si une feature touche settings/formulaires, vérifier mobile + TV quand applicable.

## 4) Commandes utiles

Build rapide debug:

```bash
bash ./build.sh debug freeBundleDebug
```

Extraire erreurs de compilation:

```bash
bash ./build.sh debug freeBundleDebug 2>&1 | grep -E "^e: |error: " | head -60
```

Vérifier une réponse WebDAV locale:

```bash
curl -i -X PROPFIND -H 'Depth: 1' http://localhost:32822/ | head -80
```

## 5) Définition de done (DoD)

Un lot est terminé seulement si:
- le build debug est vert,
- le comportement demandé est vérifié,
- le README et cette doc sont alignés avec l'état réel du code.
