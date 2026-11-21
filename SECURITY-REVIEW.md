# Revisión previa a la publicación de MotionPad

Para pruebas internas, usar una red local de confianza.

| Prioridad | Evidencia en el proyecto | Impacto y cambio recomendado |
| --- | --- | --- |
| Alta en redes compartidas | Handset.kt difunde el código durante el descubrimiento y acepta el mismo valor como respuesta. Settings.kt conserva ese código sin caducidad. Router.kt asocia el jugador a una IP y comprueba CRC32. | Otro equipo en la red puede conocer el código y suplantar un extremo. Los datos de movimiento y botones viajan en claro y faltan autenticación de cada mensaje y protección contra repeticiones. Usar descubrimiento sin secretos, emparejamiento autenticado y un canal cifrado con protección contra repetición entre teléfono y consola. Mantener DSU local para Dolphin. |
| Media, requiere otra app en el dispositivo | La actividad principal es pública. MotionPadPackage.kt acepta una indicación de modo consola del intent de apertura o reanudación y puede iniciar el servicio. | Una app local podría activar el receptor sin que el usuario elija consola. Reservar el cambio de modo para la interfaz propia y usar una ruta privada para reabrir desde la notificación. |
| Baja, local | Router.kt mantiene suscripciones DSU por dirección y puerto con caducidad de seis segundos, sin límite de suscripciones simultáneas. | Otro proceso local podría elevar el consumo de memoria y CPU. Limitar las suscripciones y mantener la caducidad. |

## Dependencias

React Native 0.76.9 está fuera de soporte. npm audit del archivo de bloqueo contabilizó 15 entradas, una alta, ocho moderadas y seis bajas, incluidas las propagadas a paquetes dependientes. La alta corresponde a image-size 1.2.1 usado por Metro. El hallazgo afecta al procesamiento de imágenes durante el empaquetado. Actualizar la herramienta y comprobar la compatibilidad. Repetir también la revisión de las dependencias Android antes de publicar.

## Alcance

Revisión estática del código React Native y Kotlin, manifiesto de release, firma y dependencias npm. Se ejecutaron el tipado, las dos pruebas JavaScript y las siete pruebas Kotlin, incluidas las de UDP local. Quedan pendientes pruebas en dispositivos físicos y una auditoría de las dependencias nativas. El material de firma privado debe permanecer fuera del repositorio público.
