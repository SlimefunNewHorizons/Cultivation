# Guia de Cruces de DrakesCraft

Cultivation se mantiene para DrakesCraft Labs. Esta guia reemplaza las referencias
operativas a paginas externas que pueden quedar desactualizadas.

## Fuente de verdad

El **Diccionario de Cruces** dentro de la guia de Slimefun es la referencia para
jugadores. Lee el registro de recetas cargado por el servidor, por lo que siempre
coincide con la version activa del addon.

Para desarrollo, el catalogo canonico vive en
`src/main/java/dev/sefiraat/cultivation/implementation/slimefun/items/Plants.java`.
No copies tablas de terceros: una receta solo existe si esta registrada ahi y se
carga al iniciar el servidor.

## Como cruzar plantas

1. Coloca dos plantas maduras compatibles en cultivos cruzados.
2. Deja aire en la casilla central; no la bloquees con otro bloque.
3. Mantiene el soporte de tierra valido bajo cada cultivo.
4. Espera el ciclo de crecimiento; el resultado aparece en el cruce central.
5. Consulta el **Diccionario de Cruces** desde la guia de Slimefun para confirmar
   Planta A, Planta B y resultado.

## Particulas negras

Las particulas negras no son una sancion ni un bloqueo de cuenta. Significan que
esa pareja no tiene una receta registrada en el catalogo actual. Cambia una de las
dos plantas y revisa el Diccionario de Cruces.

## Ejemplo importante: linea Wither

La progresion actual es:

```text
Skeleton + Power -> Wither Skeleton
Wither Skeleton + Power -> Wither
Wither + Power -> Wither Rose
```

Si una guia externa muestra otra combinacion, usa esta guia y el Diccionario de
Cruces del servidor. Son los registros que ejecuta DrakesCraft.

## Soporte y reportes

Reporta bugs con el nombre exacto de las dos plantas, el mundo, coordenadas y una
captura del Diccionario de Cruces. Los reportes del fork se gestionan en
https://github.com/SlimefunNewHorizons/Cultivation_Updated/issues.

## Licencia y procedencia

Cultivation_Updated es una version modificada para DrakesCraft Labs, distribuida
bajo GPL-3.0. Se conservan los avisos de licencia y autoria original requeridos.
