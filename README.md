# Mini-DBManager by Eleana Reyes para Teoría de Base de Datos II
Este proyecto fue desarrollado en Java Swing UI con conexión a MariaDB y replicación básica a PostgreSQL.  
Su objetivo es mostrar la gestión de bases de datos de manera visual (árbol de objetos, diagramas Entidad-Relación) y realizar replicación sencilla entre motores, sin usar information_schema.



## Lo que está incluido en el proyecto
1. Gestión de conexiones
   - Creación de múltiples conexiones (MariaDB y PostgreSQL).
   - Persistencia en un archivo JSON local.
   - Reconexión automática a la última conexión usada.

2. Exploración de objetos (MariaDB)
   - Visualización de Tablas, Columnas, Índices, Triggers, Vistas, Procedimientos, Funciones y Usuarios.
   - DDL de cada objeto mediante 'SHOW CREATE ...'.
   - Formularios gráficos para crear Tablas y Vistas.

3. Editor SQL
   - Ejecución de sentencias con resultados en tabla.
   - Importar y exportar scripts '.sql'.
   - Panel de salida con mensajes claros de éxito o error.

4. Diagramas Entidad-Relacion
   - Diagrama general de todas las tablas de la base.
   - Diagramas individuales por tabla.
   - Columnas con etiquetas [PK] y [FK].
   - Relaciones foráneas dibujadas entre tablas.
   - Panel con scroll dinámico para diagramas grandes.

5. Replicación básica hacia PostgreSQL
   - Traducción automática de 'CREATE TABLE', 'INSERT' y 'CREATE VIEW'.
   - Adaptación de diferencias sintácticas (AUTO_INCREMENT, backticks, 'ENGINE=InnoDB', etc.).
   - Soporte de 'ON CONFLICT' en 'INSERT' para simular 'ON DUPLICATE KEY UPDATE'.



## Lo que no está incluido
- Exploración de objetos en PostgreSQL, pero solo ejecución de SQL.  
- Replicación de procedimientos, triggers o funciones.  
- Replicación de sentencias complejas como 'ALTER', 'DROP', 'TRUNCATE', 'RENAME'.  
- Parser avanzado de DDL, donde se usan expresiones regulares, con limitaciones.  
- Replicación bidireccional que solo funciona de MariaDB → PostgreSQL.  
- Algoritmos avanzados de auto-organización del diagrama donde se usa rejilla básica.  



## Estructura Principal del Proyecto
- 'Conexion.java' → Manejo de la conexión JDBC.  
- 'ConexionInfo.java' → Datos de una conexión como parametros nombre, host, puerto, usuario, etc.  
- 'Global.java' → Configuración global y persistencia de conexiones.  
- 'MainFrame.java' → Ventana principal de editor SQL, árbol de objetos, etc.  
- 'CrearTablaDialog.java' → Formulario para crear tablas.  
- 'CrearVistaDialog.java' → Formulario para crear vistas.  
- 'DiagramaER.java' → Generación del diagrama entidad-relación.  



## Observaciones del Proyecto
- El proyecto cumple con los requisitos principales:  
  - No usa information_schema.  
  - Permite visualizar objetos y diagramas ER en MariaDB.  
  - Integra una replicación funcional hacia PostgreSQL.  
- La replicación está limitada, pero permite demostrar la conversión de sentencias básicas entre motores.  
- El código está organizado en clases modulares y con persistencia de configuraciones en JSON.