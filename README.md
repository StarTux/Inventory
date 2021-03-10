# Inventory

Serialize player inventories and more to the database. This should
allow carrying items from one server to another.

## Caveats

The following caveats with items must be considered.

- **Maps**. Maps are server specific and would break if carried to
    another server. We choose to wrap them into a custom item where
    they remain until returned to their proper server.
- **Mytems**. Custom items should be serialized as such, because we
    can.
