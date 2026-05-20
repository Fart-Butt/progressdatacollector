create table minecraft_players
(
    player_name       varchar(100)                       not null
        primary key,
    player_guid       varchar(100)                       not null,
    date_of_discovery datetime default (utc_timestamp()) not null
);

create index minecraft_players_player_name_IDX
    on minecraft_players (player_name);

