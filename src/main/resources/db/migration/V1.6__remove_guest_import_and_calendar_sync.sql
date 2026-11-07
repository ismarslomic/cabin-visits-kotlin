-- The guests.json import and the Google Calendar poll are removed, so nothing reads these any more.

-- The marker of guest fields changed through the API protected them from the guests.json import. The column has no
-- index, constraint or view, so it can be dropped. The edited values stay, only the marker is lost.
alter table guest
    drop column api_edited_fields;

-- The sync token of the Google Calendar poll. The other checkpoints (vehicle_trips, check_in_out_sensor) stay.
delete
from sync_checkpoint
where id = 'google_calendar_events';
