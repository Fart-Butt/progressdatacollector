create table mobkills
(
    player_name varchar(100) null,
    datetime    datetime     null,
    target      varchar(100) null,
    constraint mobkills_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

