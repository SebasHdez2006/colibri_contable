---
globs: "**/*.java"
regex: public\s+(?:final\s+)?(?:class|interface|enum)|public\s+.*\(
description: Aplicar siempre en archivos Java del proyecto para mantener la
  documentación sincronizada con el comportamiento real.
alwaysApply: true
---

Documenta en español con Javadoc todos los tipos públicos, constructores y métodos públicos; añade comentarios inline solo cuando una regla de negocio, validación o decisión técnica no sea obvia.