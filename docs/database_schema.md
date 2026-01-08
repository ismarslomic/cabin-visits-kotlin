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
        string created_time
        string updated_time
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
        smallint version
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
        smallint version
    }

    reservation ||--o{ reservation_guest: "has"
    guest ||--o{ reservation_guest: "is assigned to"
```

### Table Descriptions

| Table                   | Description                                                                                                      | Type of data  |
|:------------------------|:-----------------------------------------------------------------------------------------------------------------|:--------------|
| **check_in_out_sensor** | Stores sensor data for check-in and check-out events, including timestamps and status.                           | raw           |
| **guest**               | Stores unique guest profiles, including name, contact details, and external identifiers.                         | raw           |
| **reservation**         | Stores cabin reservations, including planned start/end times and calculated actual check-in and check-out times. | raw + derived |
| **reservation_guest**   | Links reservations with guests in a many-to-many relationship.                                                   | derived       |
| **sync_checkpoint**     | Stores the last synchronization checkpoints for various data sources.                                            | raw           |
| **vehicle_trip**        | Stores details for all vehicle trips, including consumption, distance, duration, locations, and timestamps.      | raw           |

