-- Channel credentials are validated against MySQL NOW(3). Historical
-- application-local timestamps can appear in the future when MySQL runs in UTC.
-- A current key is always intended to be valid immediately, so repair only that
-- narrowly defined case.
UPDATE scx_channel_credential credential
JOIN scx_channel_client client
  ON client.id = credential.channel_client_id
 AND client.current_key_version = credential.key_version
SET credential.valid_from = NOW(3)
WHERE credential.status = 'ACTIVE'
  AND credential.valid_from > NOW(3);
