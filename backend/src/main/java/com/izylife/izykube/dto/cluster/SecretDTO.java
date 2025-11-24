package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SecretDTO extends ConfigMapDTO {

    public SecretDTO(String id, String name, String yaml) {
        super(id, name, yaml, true, "secret");
    }

    @JsonCreator
    public SecretDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("yaml") String yaml,
            @JsonProperty("secret") Boolean secret
    ) {
        super(id, name, yaml, true, "secret");
    }
}
