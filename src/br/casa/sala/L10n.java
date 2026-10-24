package br.casa.sala;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small built-in UI dictionary. English is selected for English system locales; all others use Brazilian Portuguese. */
final class L10n {
    private static final Map<String, String> EN = new HashMap<>();
    static {
        put("Início", "Home"); put("Filmes", "Movies"); put("Séries", "TV Shows"); put("Apps", "Apps");
        put("Para você", "For You"); put("Continue de onde parou", "Continue Watching"); put("Apps frequentes", "Frequent Apps");
        put("Minha lista", "My List"); put("Clima", "Weather"); put("Cinema", "Cinema"); put("Lembretes de cinema", "Cinema Reminders");
        put("Seus apps", "Your Apps"); put("Buscar", "Search"); put("🎙  Falar", "🎙  Speak"); put("Local", "Local");
        put("Buscar cinemas e horários", "Find cinemas and showtimes"); put("Ativar localização para buscar cinemas próximos", "Enable location to find nearby cinemas");
        put("Buscar mais cinemas", "Find more cinemas"); put("Buscar cinema", "Find cinema"); put("Buscar no cinema", "Search cinemas");
        put("Sessões próximas. Os horários e links são fornecidos pelo Open Cinema Project.", "Nearby showtimes. Times and links are provided by the Open Cinema Project.");
        put("Buscar mais cinemas", "Find more cinemas"); put("Lembrete de cinema", "Cinema reminder"); put("Sessão de cinema", "Cinema showtime");
        put("Buscar cinema", "Find cinema"); put("Dispensar", "Dismiss"); put("Remover lembrete", "Remove reminder");
        put("Escolha um horário futuro.", "Choose a future time."); put("avisado", "notified");
        put("Clima e cinema perto de você", "Weather and cinemas near you");
        put("Permita a localização para mostrar a previsão local e encontrar cinemas próximos. A localização fica neste aparelho. Você também pode informar uma cidade manualmente.",
                "Allow location access to show the local forecast and find nearby cinemas. Your location stays on this device. You can also enter a city manually.");
        put("Permitir localização", "Allow location"); put("Informar cidade", "Enter a city"); put("Agora não", "Not now");
        put("Localização indisponível", "Location unavailable");
        put("A TV não forneceu uma posição atual. Informe sua cidade para configurar o clima e a busca de cinemas.", "The TV did not provide a current location. Enter your city to set up weather and cinema search.");
        put("Depois", "Later"); put("Cidade, estado ou CEP", "City, state, or ZIP code"); put("Sua cidade", "Your city");
        put("Buscar", "Search"); put("Cancelar", "Cancel"); put("Clima e localização", "Weather and location");
        put("Previsão detalhada", "Detailed forecast"); put("Alterar cidade", "Change city"); put("Usar localização da TV", "Use TV location");
        put("Remover localização", "Remove location"); put("Alterar localização", "Change location"); put("Fechar", "Close");
        put("Hoje", "Today"); put("Continuar", "Continue"); put("Parar", "Stop");
        put("Pesquisar no YouTube", "Search YouTube"); put("Criar lembrete", "Create reminder");
        put("Nenhum navegador disponível para abrir o ingresso.", "No browser is available to open the ticket link.");
        put("O aplicativo do YouTube não está disponível para abrir a busca.", "The YouTube app is not available to open this search.");
        put("Nenhum navegador disponível para abrir cinemas próximos.", "No browser is available to search nearby cinemas.");
        put("Horário não informado", "Time not provided"); put("Nenhuma sessão próxima · segure OK para horários", "No nearby showtimes · hold OK for times");
        put(" sessões próximas · segure OK para horários", " nearby showtimes · hold OK for times");
        put(". Toque para pesquisar no YouTube.", ". Select to search YouTube.");
        put(" Segure OK para criar lembrete.", " Hold OK to create a reminder.");
        put(" Segure OK para ver sessões e criar lembrete.", " Hold OK to see showtimes and create a reminder.");
        put("Pesquisar no YouTube", "Search YouTube"); put("cinemas e horários de cinema", "cinemas and showtimes");
        put("O que você quer assistir?", "What do you want to watch?");
        put("A pesquisa por voz não está disponível nesta TV.", "Voice search is not available on this TV.");
        put("Não foi possível iniciar o reconhecimento de voz.", "Could not start voice recognition.");
        put("Não foi possível iniciar o reconhecimento de voz.", "Could not start voice recognition.");
        put("Digite um nome", "Enter a name");
        put("Hoje", "Today"); put("HOJE", "TODAY"); put("Assistido no ", "Watched on "); put("Assistir no ", "Watch on ");
        put("Loja", "Store"); put("Carregar mais", "Load more"); put("Gerenciar apps", "Manage apps");
        put("Apps que aparecem na tela", "Apps shown on screen");
        put("Selecione um ícone para mostrar ou ocultar em Seus apps.", "Select an icon to show or hide it in Your Apps.");
        put("←  Voltar aos apps", "←  Back to apps"); put("Nenhum app instalável encontrado.", "No launchable apps found.");
        put("Nada por aqui ainda. O que você assistir no ", "Nothing here yet. What you watch on ");
        put(" aparece nesta fileira.", " will appear in this row.");
        put("Nada encontrado para “", "No results for “"); put("”. Tente procurar dentro de um app abaixo.", "”. Try searching inside one of the apps below.");
        put("Procurar no ", "Search in "); put("Quem está assistindo?", "Who's watching?"); put("Usuário", "User"); put("Adicionar", "Add");
        put("Segure OK num perfil para editar", "Hold OK on a profile to edit it"); put("Trocar perfil", "Switch profile"); put("Perfis", "Profiles");
        put("Perfil protegido · ", "Profile protected · "); put("PIN do perfil", "Profile PIN"); put("Continuar", "Continue");
        put("PIN incorreto.", "Incorrect PIN."); put("Crie um PIN com 4 ou mais números", "Create a PIN with at least 4 digits");
        put("Confirme o PIN", "Confirm PIN"); put("Proteja o perfil Kids", "Protect the Kids profile");
        put("Defina um PIN para sair do perfil e alterar suas restrições.", "Set a PIN to leave this profile and change its restrictions.");
        put("Salvar PIN", "Save PIN"); put("Os PINs precisam ter 4 ou mais números e ser iguais.", "PINs must have at least 4 digits and match.");
        put("Crie seu perfil", "Create your profile"); put("Novo perfil", "New profile"); put("Editar perfil", "Edit profile");
        put("Personalize quem está assistindo e o que pode acessar.", "Customize who's watching and what they can access.");
        put("Perfil", "Profile"); put("Avatar", "Avatar"); put("Conteúdo e privacidade", "Content and privacy");
        put("Perfil infantil", "Kids profile"); put("Não", "No"); put("Sim", "Yes");
        put("Exigir PIN ao entrar em Kids", "Require a PIN to enter Kids");
        put("Exigir cadastro de PIN ao entrar neste perfil Kids", "Require a PIN when entering this Kids profile");
        put("Desligado por padrão. Ligado: o PIN será cadastrado na primeira entrada e protegerá a saída e as restrições.",
                "Off by default. When enabled, set a PIN on first entry to protect leaving the profile and changing restrictions.");
        put("Faixa etária", "Age limit"); put("Limita as recomendações e os apps pela idade selecionada.", "Limits recommendations and apps based on the selected age.");
        put("Salvar", "Save"); put("Excluir perfil", "Delete profile"); put("Digite um nome", "Enter a name"); put("Nome", "Name");
        put("Aviso de sono definido para ", "Sleep reminder set for "); put(" minutos.", " minutes.");
        put("Hora de dormir", "Bedtime"); put("O temporizador terminou. A TV pode ser colocada em espera pelo controle remoto ou pelas configurações do sistema.",
                "The timer has ended. Put the TV in standby with the remote or system settings.");
        put("Launcher TV", "Launcher TV"); put("Mostrar apps ocultos (", "Show hidden apps ("); put("Limpar histórico", "Clear history");
        put("Wi-Fi", "Wi-Fi"); put("Sobre", "About"); put("Acessibilidade", "Accessibility");
        put("Ajustes para leitura e navegação pelo controle remoto.", "Options for readability and remote navigation.");
        put("Temporizador de sono", "Sleep timer"); put("15 minutos", "15 minutes"); put("30 minutos", "30 minutes");
        put("60 minutos", "60 minutes"); put("90 minutos", "90 minutes"); put("Cancelar temporizador", "Cancel timer");
        put("Não foi possível iniciar o reconhecimento de voz.", "Could not start voice recognition.");
        put("Não encontrei essa cidade. Tente informar cidade e estado.", "City not found. Try entering the city and state.");
        put("Local configurado: ", "Location set: ");
        put("Configure uma localização para ver clima e buscar cinemas próximos.", "Set a location to see the weather and find nearby cinemas.");
        put("Destaques indisponíveis.", "Highlights are unavailable."); put("Carregando destaques…", "Loading highlights…");
        put("Umidade ", "Humidity "); put(" · Vento ", " · Wind "); put(" km/h · UV ", " km/h · UV ");
        put("Próximos dias", "Coming days"); put(" · chuva ", " · rain "); put("Próximas horas", "Coming hours");
        put("Digite ou fale o que você procura. A busca olha filmes, séries e apps instalados.", "Type or say what you're looking for. Search covers movies, TV shows, and installed apps.");
        put("Buscando “", "Searching for “"); put("”…", "”…");
        put("Filmes e séries", "Movies and TV Shows"); put(" dentro do app", " in the app");
        put(" · app não instalado", " · app not installed"); put(", na minha lista. Segure OK para remover.", ", in My List. Hold OK to remove.");
        put(", segure OK para salvar na minha lista.", ", hold OK to add to My List.");
        put("Adicionado à Minha lista", "Added to My List"); put("Removido da Minha lista", "Removed from My List");
        put("Ajustes", "Settings"); put("Aplicativos", "Apps");
        put("Informações do app", "App info"); put("Remover dos favoritos", "Remove from favorites");
        put("Adicionar aos favoritos", "Add to favorites"); put("Tirar de Seus apps", "Remove from Your Apps");
        put("Colocar em Seus apps", "Add to Your Apps"); put("Ocultar app", "Hide app"); put("Desinstalar app", "Uninstall app"); put("Mover", "Move");
        put("Texto maior", "Larger text"); put("Foco mais visível", "More visible focus"); put("Contorno forte", "Strong outline");
        put("Texto maior ✓", "Larger text ✓"); put("Foco mais visível ✓", "More visible focus ✓"); put("Contorno forte ✓", "Strong outline ✓");
        put("O launcher pode avisar quando o tempo terminar. Colocar a TV em espera depende da ação que o firmware TCL permite a apps comuns.",
                "The launcher can notify you when the timer ends. Putting the TV in standby depends on what the TCL firmware allows regular apps to do.");
        put("Temporizador cancelado.", "Timer cancelled.");
        put("Clima. Toque para configurar a localização.", "Weather. Select to set a location."); put("Clima em ", "Weather in ");
        put(" graus. Toque para ver a previsão detalhada.", " degrees. Select to view the detailed forecast.");
        put("Céu limpo", "Clear sky"); put("Predominantemente limpo", "Mainly clear"); put("Parcialmente nublado", "Partly cloudy");
        put("Nublado", "Overcast"); put("Neblina", "Fog"); put("Garoa", "Drizzle"); put("Chuva", "Rain"); put("Neve", "Snow");
        put("Pancadas de chuva", "Rain showers"); put("Pancadas de neve", "Snow showers"); put("Trovoadas", "Thunderstorms");
        put("Condição indisponível", "Condition unavailable");
        put("A busca olha filmes, séries e apps instalados.", "Search covers movies, TV shows, and installed apps.");
        put("Acessibilidade", "Accessibility"); put("Mostrar apps ocultos", "Show hidden apps");
        put("Configurações do Android", "Android settings"); put("Configurações da TV", "TV settings");
        put("Continuar no ", "Continue on "); put("Filmes", "Movies"); put("Séries", "TV Shows");
        put("Trocar", "Switch"); put("Criar perfil", "Create profile"); put("Excluir", "Delete"); put("Favoritos", "Favorites"); put("Tamanho", "Size");
        put("Anos", "Years"); put("anos", "years");
        put("Reproduzindo agora", "Now playing"); put("agora", "now"); put("parou em", "stopped at");
        put(", visível. Toque para ocultar.", ", visible. Select to hide."); put(", oculto. Toque para mostrar.", ", hidden. Select to show.");
        put("Destaques de filmes e séries: dados e imagens do TMDB. Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB. Temperatura: Open-Meteo.",
                "Movie and TV show highlights: data and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. Weather: Open-Meteo.");
        put("Filme", "Movie"); put("Série", "TV Show");
        put("Ajustes", "Settings"); put("Aplicativos", "Apps"); put("Visível", "Visible"); put("Oculto", "Hidden");
        put("Abrir", "Open"); put("Assistido recentemente", "Recently watched"); put("Tamanho do texto", "Text size");
        put("Foco", "Focus"); put("Contraste", "Contrast"); put("Padrão", "Default"); put("Aumentado", "Large");
        put("Buscar na web", "Search the web"); put("Digite um nome", "Enter a name");
    }

    private L10n() {}
    private static void put(String pt, String en) { EN.put(pt, en); }
    static boolean isEnglish(Context c) {
        Locale locale = c.getResources().getConfiguration().getLocales().isEmpty()
                ? Locale.getDefault() : c.getResources().getConfiguration().getLocales().get(0);
        return "en".equalsIgnoreCase(locale.getLanguage());
    }
    static String language(Context c) { return isEnglish(c) ? "en-US" : "pt-BR"; }
    static String profileName(Context c, String name) {
        if ("User".equals(name) || "Usuário".equals(name)) return isEnglish(c) ? "User" : "Usuário";
        return name;
    }
    static String tr(Context c, String value) {
        if (value == null || !isEnglish(c)) return value;
        String translated = EN.get(value);
        if (translated != null) return translated;
        if (value.startsWith("Filme · ")) return "Movie · " + value.substring("Filme · ".length());
        if (value.startsWith("Série · ")) return "TV Show · " + value.substring("Série · ".length());
        if (value.startsWith("Assistir no ")) return "Watch on " + value.substring("Assistir no ".length());
        if (value.startsWith("Cinema · ")) return "Cinema · " + value.substring("Cinema · ".length());
        if (value.endsWith(" sessões próximas · segure OK para horários")) return value.substring(0, value.length() - " sessões próximas · segure OK para horários".length()) + " nearby showtimes · hold OK for times";
        if (value.endsWith(". Toque para pesquisar no YouTube. Segure OK para criar lembrete.")) return value.substring(0, value.length() - ". Toque para pesquisar no YouTube. Segure OK para criar lembrete.".length()) + ". Select to search YouTube. Hold OK to create a reminder.";
        if (value.endsWith(". Toque para pesquisar no YouTube. Segure OK para ver sessões e criar lembrete.")) return value.substring(0, value.length() - ". Toque para pesquisar no YouTube. Segure OK para ver sessões e criar lembrete.".length()) + ". Select to search YouTube. Hold OK to see showtimes and create a reminder.";
        if (value.startsWith("Clima · ")) return "Weather · " + value.substring("Clima · ".length());
        if (value.startsWith("Clima em ")) return "Weather in " + value.substring("Clima em ".length());
        if (value.startsWith("Cinema · ")) return "Cinema · " + value.substring("Cinema · ".length());
        if (value.startsWith("Humidade ")) return "Humidity " + value.substring("Humidade ".length());
        if (value.startsWith("Umidade ")) return "Humidity " + value.substring("Umidade ".length());
        if (value.startsWith("Local configurado: ")) return "Location set: " + value.substring("Local configurado: ".length());
        if (value.startsWith("Aviso de sono definido para ")) return "Sleep reminder set for " + value.substring("Aviso de sono definido para ".length()).replace(" minutos.", " minutes.");
        if (value.startsWith("Assistido no ")) return "Watched on " + value.substring("Assistido no ".length());
        if (value.startsWith("Assistir no ")) return "Watch on " + value.substring("Assistir no ".length());
        if (value.startsWith("Procurar no ")) return "Search in " + value.substring("Procurar no ".length());
        if (value.startsWith("Nada por aqui ainda. O que você assistir no ") && value.endsWith(" aparece nesta fileira.")) {
            String app = value.substring("Nada por aqui ainda. O que você assistir no ".length(), value.length() - " aparece nesta fileira.".length());
            return "Nothing here yet. What you watch on " + app + " will appear in this row.";
        }
        if (value.startsWith("Buscando “") && value.endsWith("”…")) {
            String query = value.substring("Buscando “".length(), value.length() - "”…".length());
            return "Searching for “" + query + "”…";
        }
        if (value.startsWith("Nada encontrado para “") && value.endsWith("”. Tente procurar dentro de um app abaixo.")) {
            String query = value.substring("Nada encontrado para “".length(), value.length() - "”. Tente procurar dentro de um app abaixo.".length());
            return "No results for “" + query + "”. Try searching inside one of the apps below.";
        }
        if (value.startsWith("Até ") && value.endsWith(" anos")) return "Up to " + value.substring(4, value.length() - 5);
        if (value.startsWith("Até ") && value.endsWith(" ano")) return "Up to " + value.substring(4, value.length() - 4);
        if (value.startsWith(" · chuva ")) return " · rain " + value.substring(" · chuva ".length());
        if (value.startsWith("° · chuva ")) return "° · rain " + value.substring("° · chuva ".length());
        if (value.startsWith("Continuar no ")) return "Continue on " + value.substring("Continuar no ".length());
        if (value.startsWith("Continue")) value = value.replace("Continuar", "Continue").replace("agora", "now");
        value = value.replace("parou em ", "stopped at ").replace("· chuva ", "· rain ").replace("· chuva", "· rain");
        if (value.endsWith(", na minha lista. Segure OK para remover.")) return value.substring(0, value.length() - ", na minha lista. Segure OK para remover.".length()) + ", in My List. Hold OK to remove.";
        if (value.endsWith(", segure OK para salvar na minha lista.")) return value.substring(0, value.length() - ", segure OK para salvar na minha lista.".length()) + ", hold OK to add to My List.";
        if (value.endsWith(", visível. Toque para ocultar.")) return value.substring(0, value.length() - ", visível. Toque para ocultar.".length()) + ", visible. Select to hide.";
        if (value.endsWith(", oculto. Toque para mostrar.")) return value.substring(0, value.length() - ", oculto. Toque para mostrar.".length()) + ", hidden. Select to show.";
        return value;
    }
}
