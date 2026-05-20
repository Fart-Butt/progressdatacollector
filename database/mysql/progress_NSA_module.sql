create table progress_NSA_module
(
    player_name varchar(100)             not null,
    datetime    datetime default (now()) not null,
    dimension   varchar(30)              not null,
    x           int                      not null,
    y           int                      not null,
    z           int                      not null,
    constraint progress_NSA_module_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

