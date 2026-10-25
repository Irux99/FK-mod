# Compatibilité Superb Warfare

## État vérifié

Les API ont été examinées dans les sources officielles de [Mercurows/SuperbWarfare](https://github.com/Mercurows/SuperbWarfare), puis dans les **JAR de sources exacts publiés sur Modrinth** pour les trois distributions demandées. Les six JAR officiels, sources et binaires, ont été téléchargés et vérifiés avec leur SHA-512 publié par l'API officielle ; leurs références et empreintes figurent dans [SBW-ARTIFACTS.json](SBW-ARTIFACTS.json). `javap` confirme les signatures publiques utilisées dans chacun des trois binaires. Cela ne remplace pas les essais de jeu ni la validation multijoueur.

| Référence officielle inspectée | Commit exact | Version déclarée par les sources |
| --- | --- | --- |
| branche `0.8.9-1.20` | `6effe43854363265b04d453c0b0bcf76994c2944` | `0.8.9` |
| tag `1.20-0.8.9.1` | `74b64f8ea67dbb15532292466d7ef955572c2e8a` | `0.8.9.1` |
| branche `0.8.9.1-1.20` | `993063bed60ac4d0a8d75ca9972765f4557172c8` | `0.8.9.1` |
| branche `0.8.9.2-1.20` | `65d985a6447cf6b119b72b73a636e79adecbb44f` | `0.8.9.2` |

Les noms des artefacts officiels `0.8.9-final`, `0.8.9.1-hotfix` et `0.8.9.2` portent respectivement les préfixes de commit `6effe4385`, `993063bed` et `65d985a64`, correspondant aux branches inspectées. Les JAR de sources publiés ont ensuite été examinés directement. Le tag `1.20-0.8.9.1` correspond à la distribution `.1` initiale, distincte du hotfix demandé.

## Intégration choisie

SBW est une dépendance runtime optionnelle pour permettre les essais du noyau sans SBW ; les fonctionnalités de ciblage SBW nécessitent son installation réelle.

Le FK-2000 est une entité Forge originale, avec sa conduite, son rendu et son réseau. Il n'hérite pas de `VehicleEntity` de SBW : cette classe dépend de nombreux systèmes internes de données, de scripts, de collision et d'animation. Les missiles utilisent également une entité originale et leur propre machine à états.

La couche `fr.irux.fk2000.compat.SbwCompatibility` isole les accès à SBW par réflexion. Aucun modèle, texture, son, classe ni JAR de SBW n'est incorporé au livrable.

API communes vérifiées dans les références ci-dessus :

| API SBW | Utilisation |
| --- | --- |
| `com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity` | Vérifier qu'une cible appartient au système de véhicules SBW. |
| `getVehicleType()` | Lire le type sémantique du véhicule, sans liste de noms d'entités. |
| `isWreck()` | Exclure les épaves. |
| `getLastDriverUUID()` | Contribuer à l'identification du propriétaire ou de l'équipe. |
| `com.atsuishio.superbwarfare.tools.SeekTool.IS_FRIENDLY` | Prédicat public `BiPredicate` prenant en compte équipes, propriétaires, passagers et dernier conducteur. |

Les noms d'enum `AIRPLANE`, `HELICOPTER` et `DRONE` existent dans toutes les références inspectées ; `AIRSHIP` existe dès les sources `.1`. Le code utilise les noms, jamais leur ordre numérique. Une API inconnue doit conduire à ignorer une cible ou à refuser l'engagement, avec un avertissement dans le journal.

Les autres mods peuvent exposer une entité aérienne via l'interface `AerialTarget` ou le tag d'entités `iruxfk2000:aerial_targets`. Le tag déclare qu'une entité peut être ciblée ; il faut le réserver à des véhicules aériens compatibles. Les joueurs ne sont pas des cibles radar.

Ce pont de ciblage n'établit aucune intégration avec la réparation, les munitions, le menu de véhicules, les blindages ou les systèmes de dégâts propres à SBW. Ces capacités demandent des travaux et tests distincts.

## Dépendances en jeu

- Minecraft Java **1.20.1** ; Forge **47.4.10** utilisé par ce projet.
- Java **17**.
- Pour l'intégration SBW, une distribution officielle de **Superb Warfare 0.8.9-final ou ultérieure compatible 1.20.1 Forge**. Le noyau autonome peut être lancé sans elle ; tester chaque version séparément.
- **Kotlin for Forge 4.11.0 ou ultérieur compatible**, exigé par le chargeur SBW dans les sources inspectées.
- Respecter les dépendances de chaque fichier SBW officiel. Les versions récentes peuvent embarquer des bibliothèques dans leur JAR ; éviter d'ajouter des doublons.

Les métadonnées des JAR distribués ont été inspectées : `0.8.9-final` embarque Simple Bedrock Model `2.3.3` et exige séparément Curios `>=5.4.0`, GeckoLib `>=4.4.6` et Kotlin for Forge `>=4.11.0`. Le hotfix `.1` et `.2` embarquent Curios `5.14.1`, GeckoLib `4.4.6`, Simple Bedrock Model `2.5.1`, Cloth Config `11.1.106`, Ponder `1.0.91`, Flywheel `1.0.5` et Rhino `1.8.1` ; Kotlin for Forge reste séparé.

Télécharger uniquement depuis les canaux officiels : [Modrinth](https://modrinth.com/mod/superb-warfare), les liens du dépôt officiel, et les pages officielles des dépendances. Ne pas modifier les JAR.

## Matrice des distributions demandées

| Distribution SBW officielle | Sources et API binaire | Build et serveur de tests dédié | GameTests requis | Client graphique réel | Deux clients réseau |
| --- | --- | --- | --- | --- | --- |
| `0.8.9-final` (`LAITrju0`) | JAR sources et binaire vérifiés | Réussis, exit 0 | **13/13 réussis** | Vérifié : radar AH-6, verrouillage, lancement, conduite distante, caméra et retour au joueur | Non exécuté |
| `0.8.9.1-hotfix` (`mHmtMuKF`) | JAR sources et binaire vérifiés | Réussis, exit 0 | **13/13 réussis** | Non exécuté | Non exécuté |
| `0.8.9.2` (`81Iyv9ao`) | JAR sources et binaire vérifiés | Réussis, exit 0 | **13/13 réussis** | Non exécuté | Non exécuté |

Validation terminée le **25 octobre 2026**, sur Java 17 et Forge 47.4.10. Les mêmes 13 scénarios ont été exécutés séparément avec chacune des trois distributions officielles, soit **39 exécutions GameTest réussies**. Les deux scénarios propres à SBW construisent les vrais A-10A, AH-6 et ZTZ-99A de la distribution chargée : classification aérienne, exclusion des épaves et des véhicules terrestres, prédicat IFF SBW, dernier conducteur, équipes, détection radar et lancement manuel avec éjection physique moteur éteint. Les onze autres scénarios exercent les entités originales de l'addon, notamment l'interception de cibles fixe et mobile avec dégâts serveur.

Ces résultats vérifient les interactions couvertes ; ils ne constituent pas une certification de tous les systèmes SBW ni un essai multijoueur avec deux clients. Les détails, commandes et journaux des exécutions finales sont dans [VALIDATION.md](VALIDATION.md).

L'accès Modrinth initialement refusé a été rétabli après propagation de la configuration réseau ; les téléchargements officiels ont ensuite réussi. La page GitHub des releases indiquait l'absence de releases ; les artefacts utilisés proviennent bien de Modrinth, pas des fichiers de bibliothèques internes du dépôt.

Utiliser un répertoire de jeu distinct par version, puis contrôler le démarrage dédié, le chargement client, la classification d'un avion et d'un hélicoptère, l'exclusion des alliés, le tir manuel, les phases du missile, la perte de cible et la réplication avec deux clients. Une absence d'erreur au démarrage ne valide pas ces interactions.


## Variantes de développement reproductibles

Sans paramètre, le projet développe son noyau Forge autonome. Pour sélectionner une distribution SBW :

```bash
source scripts/cloud-env.sh  # instance cloud Linux préparée
./gradlew -PsbwVersion=0.8.9-final -PrunDirectory=run/sbw-0.8.9-final build
./gradlew -PsbwVersion=0.8.9-final -PrunDirectory=run/sbw-0.8.9-final runClient
```

Remplacer la version par `0.8.9.1-hotfix` ou `0.8.9.2` et choisir un répertoire distinct pour les tester séparément. Les références Maven Modrinth sont épinglées aux identifiants de version exacts : SBW `LAITrju0` / `mHmtMuKF` / `81Iyv9ao`, Kotlin for Forge `hmeyC41q`, Curios `IPQlZkz1` et GeckoLib `iS7jylhp`. Ces deux dernières dépendances séparées sont ajoutées uniquement pour `0.8.9-final`.

ForgeGradle remappe les dépendances runtime dans son cache pour les mappings de développement. Il conserve les téléchargements officiels originaux. Le code de l'addon n'a aucune dépendance de compilation à des classes internes SBW ; une compilation réussie ne vérifie donc pas automatiquement les appels par réflexion. Les JAR de production restent ceux distribués officiellement, à installer dans `mods/` avec le JAR réobfusqué de l'addon.

## Licences

Le README officiel SBW distingue **code GPL-3.0** et **ressources « All Rights Reserved »**. Le projet FK-2000 utilise du code et des formes de rendu originaux ; il ne redistribue aucune ressource SBW. Le téléchargement et l'installation des dépendances restent séparés du JAR de l'addon.
