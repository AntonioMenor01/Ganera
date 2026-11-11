-- Alta manual de trámites (POST /tramites, origen MANUAL): el contacto es opcional.
-- Un trámite de WhatsApp sigue llevando contacto; uno creado a mano puede no tenerlo
-- (llamada telefónica, papel, etc.). WhatsApp solo responde si origen = WHATSAPP.
ALTER TABLE tramite ALTER COLUMN contacto_id DROP NOT NULL;
