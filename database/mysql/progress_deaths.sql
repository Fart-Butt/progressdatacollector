create table progress_deaths
(
    player_name      varchar(100)                        null,
    datetime         timestamp default CURRENT_TIMESTAMP not null,
    message          varchar(200)                        not null,
    world            varchar(30)                         not null,
    x                decimal(10, 2)                      not null,
    y                decimal(10, 2)                      not null,
    z                decimal(10, 2)                      not null,
    payloadUUID      varchar(100)                        null,
    type             varchar(100)                        null,
    weapon           varchar(100)                        null,
    localizationText varchar(100)                        null,
    localizationMob  varchar(100)                        null,
    `key`            varchar(100)                        null,
    constraint progress_deaths_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

create fulltext index message
    on progress_deaths (message);

