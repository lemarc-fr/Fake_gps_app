# Règles R8 propres à l'application.
#
# Rien à conserver pour l'instant : l'appli n'utilise pas de réflexion, et les bibliothèques
# embarquent leurs règles (MapLibre : JavaCPP et ponts JNI ; Compose ; Kotlin).
# Les noms d'enum enregistrés dans les préférences (MapStyle, RoutingProfile) restent stables :
# R8 conserve la valeur de Enum.name même quand il renomme la classe.

# Pile d'appels lisible dans les rapports de plantage (à décoder avec mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
