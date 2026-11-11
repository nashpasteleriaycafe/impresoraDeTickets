# Impresora de Tickets

Aplicación Android para crear y administrar diseños de tickets para comandas e impresoras térmicas.

## Incluido en esta primera base

- Proyecto Android en Kotlin con Jetpack Compose.
- Crear, guardar, editar y eliminar plantillas.
- Persistencia local de las plantillas en el dispositivo.
- Configuración inicial para papel de 58 mm y 80 mm.
- Vista previa básica del texto del ticket.
- GitHub Actions para compilar un APK.
- Publicación del APK en **GitHub Releases** al subir una etiqueta con formato `v*`.

## Compilación

En GitHub, abrir **Actions → Android APK Release → Run workflow** para probar la compilación. Para publicar un descargable en Releases, crear y subir una etiqueta, por ejemplo `v0.1.0`.

## Pendiente antes de imprimir tickets reales

La conexión USB y el protocolo ESC/POS todavía deben integrarse y probarse con el modelo concreto de impresora. El botón «Probar impresión» de esta base es informativo y no envía datos a la impresora. También queda ampliar el editor visual con filas, columnas, formato de texto, tipografías, márgenes, espaciado e imágenes/PDF.
