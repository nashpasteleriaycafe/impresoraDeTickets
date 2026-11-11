# Impresora de Tickets

Aplicación Android para crear y administrar diseños de tickets para comandas e impresoras térmicas.

## Incluido

- Proyecto Android en Kotlin con Jetpack Compose.
- Crear, guardar, editar y eliminar plantillas.
- Persistencia local de las plantillas en el dispositivo.
- Configuración de ancho de papel de 58 mm y 80 mm.
- Vista previa básica del texto del ticket.
- Impresión USB mediante Android USB Host y comandos ESC/POS, preparada para probar con la 3nStar RPT001.
- GitHub Actions para compilar un APK.
- Publicación del APK en **GitHub Releases** al subir una etiqueta con formato `v*`.

## Impresión USB

1. Encendé la impresora y cargá papel térmico de 58 mm.
2. Conectá la impresora al celular/tablet Android con un cable USB OTG compatible.
3. Abrí la app y elegí una plantilla.
4. Tocá **Imprimir ticket por USB**.
5. Si Android solicita permiso para el dispositivo USB, aceptalo y volvé a tocar el botón.

La impresión debe validarse con el equipo real. La compatibilidad depende de que el dispositivo Android admita USB Host/OTG y de cómo se presente la interfaz USB de la impresora. La primera versión envía texto ESC/POS; todavía no imprime logotipos ni ofrece formato visual avanzado.

## Compilación

En GitHub, abrir **Actions → Android APK Release → Run workflow** para probar la compilación. Para publicar un descargable en Releases, crear y subir una etiqueta, por ejemplo `v0.1.0`.

## Próximas mejoras

Ampliar el editor visual con filas, columnas, formato de texto, tipografías, márgenes, espaciado e imágenes/PDF.
