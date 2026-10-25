# Imported models and visual update

Imported visual resources — Irux FK-2000 0.2.0

Truck: user-supplied Truck.fbx / Truck.obj
Source: https://www.cgtrader.com/items/6470222/download-page
Missile: user-supplied rocket.obj and 10038.jpg
Source: https://www.cgtrader.com/free-3d-models/military/rocketry/missile-1

Licence reported by the supplying user for both models:
Royalty Free License (no AI).
These imported/derived assets are NOT covered by the project code MIT licence.
Copyright remains with the original authors identified on the source listings.
Original source archives are not redistributed in this project.
Models are converted and simplified using deterministic Blender tools; no AI
training or generative-image service is used. Truck paint is baked procedurally.
The complete licence terms could not be retrieved because the listings returned
an automated-access challenge. Public redistribution of reusable source assets
must remain blocked until the full permissions have been established.

Truck: 18,368 triangles; eight articulated wheel groups, one chassis and one launcher. Original diffuse atlas: 2048 x 2048. Missile: 418 triangles with the supplied UV-mapped diffuse texture. No SBW model or texture is incorporated.

## Reproduire les ressources localement

Disposer des licences des modèles et obtenir les fichiers depuis leurs pages d'origine. Blender 4.3 a été utilisé. Depuis le dossier du projet :

```bash
blender --background --python scripts/assets/export_truck.py -- /chemin/Truck.fbx src/main/resources/assets/iruxfk2000/mesh/truck.json
blender --background --python scripts/assets/export_missile.py -- /chemin/rocket.obj src/main/resources/assets/iruxfk2000/mesh/missile.json
```

Le premier script optimise les surfaces, sépare les huit groupes de roues et le lanceur, crée les UV et cuit le camouflage original. Convertir la texture fournie `10038.jpg` en PNG sous `textures/entity/imported-missile.png`, sans redimensionnement ; les UV du missile sont conservés. Les fichiers générés sont exclus du suivi Git. Le code public conserve un rendu de remplacement utilisable sans ces ressources.

Les géométries et textures tierces restent sous leur licence CGTrader déclarée « Royalty Free License (no AI) ». La licence MIT du code ne donne aucun droit de réutilisation indépendant sur elles. Aucun fichier OBJ, FBX ni archive d'origine n'est intégré aux téléchargements publics du projet ; les ressources de rendu sont incorporées au mod de jeu. Aucune ressource de Superb Warfare n'a été copiée.
