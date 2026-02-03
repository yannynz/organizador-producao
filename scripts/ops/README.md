# Ferramentas Operacionais (Backup/Restore)

Arquivos deste diretorio:

- `backup_postgres.sh`: backup completo de Postgres + bucket MinIO.
- `restore_backup.sh`: restore completo de Postgres + bucket MinIO.

Os scripts carregam automaticamente `/etc/organizer/organizer.env` quando o arquivo existe (instalacao via `installer/install-organizer.sh`).

Uso manual:

```bash
~/backup_database/backup_postgres.sh
~/backup_database/restore_backup.sh --choose
```
