// Runs once, when the MongoDB volume is created for the first time (as the root user).
// One database and one user per service: a service's credentials only open its own database.
// If you add a service later, run `docker compose down -v` to recreate the volume.

// catalog-service
db.getSiblingDB('catalog_db').createUser({
  user: 'catalog_user',
  pwd: 'catalog_pass',
  roles: [{ role: 'readWrite', db: 'catalog_db' }],
});

// notification-service
db.getSiblingDB('notification_db').createUser({
  user: 'notification_user',
  pwd: 'notification_pass',
  roles: [{ role: 'readWrite', db: 'notification_db' }],
});
