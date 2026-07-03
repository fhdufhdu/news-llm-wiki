package com.newswiki.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {
    @Test
    void bindsDefaultsFromApplicationYaml() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var source = loader.load("application", new ClassPathResource("application.yml")).getFirst();
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(source);

        var props = Binder.get(env)
                .bind("newswiki", Bindable.of(AppProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(props.aiBatchSize()).isEqualTo(80);
        assertThat(props.dailyRebuildMaxBatches()).isEqualTo(25);
        assertThat(props.aiMaxRetries()).isEqualTo(3);
        assertThat(props.articleFetchMaxRetries()).isEqualTo(3);
        assertThat(props.rssFeedEntryLimit()).isEqualTo(5);
        assertThat(props.articleFetchInsecureSsl()).isFalse();
        assertThat(props.rawStorageMode()).isEqualTo("SQLITE_TEXT");
    }
}
