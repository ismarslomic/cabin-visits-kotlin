## Database schema

```mermaid
erDiagram
    check_in_out_sensor {
        string id PK
        string created_time
        string updated_time
        smallint version
        string time
        string status
    }

    sync_checkpoint {
        string id PK
        string created_time
        string updated_time
        smallint version
        string checkpoint_value
    }

    guest {
        string id PK
        string first_name
        string last_name
        smallint birth_year
        string email
        string gender
        string notion_id
        string role
        string created_time
        string updated_time
        string created_by
        string updated_by
        smallint version
    }

    reservation {
        string id PK
        string summary
        string description
        string start_time
        string end_time
        string check_in_time
        string check_out_time
        string notion_id
        string created_time
        string updated_time
        string created_by
        string updated_by
        smallint version
    }

    guest_avatar {
        string guest_id PK, FK
        blob image
        string created_time
        string created_by
        string updated_time
        string updated_by
    }

    reservation_guest {
        string reservation_id PK, FK
        string guest_id PK, FK
    }

    vehicle_trip {
        string id PK
        double avg_energy_consumption
        double distance
        bigint duration
        string start_city
        string end_city
        string start_time
        string end_time
        string notion_id
        string created_time
        string updated_time
        string created_by
        string updated_by
        smallint version
    }

    reservation ||--o{ reservation_guest: "has"
    guest ||--o{ reservation_guest: "is assigned to"
    guest ||--o| guest_avatar: "has"
```

### Table Descriptions

| Table                   | Description                                                                                                      | Type of data  |
|:------------------------|:-----------------------------------------------------------------------------------------------------------------|:--------------|
| **check_in_out_sensor** | Stores sensor data for check-in and check-out events, including timestamps and status.                           | raw           |
| **guest**               | Stores unique guest profiles, including name, contact details, and external identifiers.                         | raw           |
| **guest_avatar**        | Stores the JPEG avatar of a guest (at most one per guest, deleted with the guest).                               | raw           |
| **reservation**         | Stores cabin reservations, including planned start/end times and calculated actual check-in and check-out times. | raw + derived |
| **reservation_guest**   | Links reservations with guests in a many-to-many relationship.                                                   | derived       |
| **sync_checkpoint**     | Stores the last synchronization checkpoints for various data sources.                                            | raw           |
| **vehicle_trip**        | Stores details for all vehicle trips, including consumption, distance, duration, locations, and timestamps.      | raw           |

### Audit columns

`reservation`, `guest`, `guest_avatar` and `vehicle_trip` have four audit columns, in addition to `version` (not on
`guest_avatar`):

| Column         | Required | Set when                                          | Value                                                  |
|:---------------|:---------|:--------------------------------------------------|:-------------------------------------------------------|
| `created_time` | yes      | the row is created, never changed                 | now                                                    |
| `created_by`   | yes      | the row is created, never changed                 | the authenticated user's `guest.id`, or `system`       |
| `updated_time` | no       | an existing row is changed, otherwise null        | now                                                    |
| `updated_by`   | no       | an existing row is changed, otherwise null        | the authenticated user's `guest.id`, or `system`       |

- `system` is used by the vehicle trip import and other tasks. It is not a guest, so there is no foreign key from
  `created_by`/`updated_by` to `guest`; the columns are plain text. Rows that existed when `V1.7` ran have
  `created_by = 'system'`, and `updated_by = 'system'` where `updated_time` was set.
- `created_by` has the database default `system` on `reservation`, `guest` and `vehicle_trip` as a safety net: the
  application always sets the actor explicitly. `guest_avatar` has no default.
- The columns change only when the row is actually changed (an unchanged save does not touch them). `version` and
  `updated_*` of a reservation cover user edits (times, guests) only: check-in/out and vehicle trip links are derived
  data and change neither.
- An avatar's version time (the `v` in `avatarUrl` and the `ETag`) is `updated_time`, or `created_time` when the avatar
  was never replaced. Replacing an avatar changes only the avatar row, not the guest's `version`.
