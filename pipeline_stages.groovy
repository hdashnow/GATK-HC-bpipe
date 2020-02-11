call_variants_hc = {
  doc "Call single sample HaplotypeCaller to produce a GVCF"
    output.dir="variants"

// set TARGET to appropriate chromosomes?
    transform("bam") to("vcf") {
        exec """

            $GATK --java-options "-Xmx32g" HaplotypeCaller
                   -R $REF
                   -I $input.bam
                   --dbsnp $DBSNP
                   -L $TARGET
                   -O $output.gvcf
                   -ERC GVCF
            """
    }
}

combine_gvcfs = {
  doc "Combine GVCF from multiple samples to do joint calling"
    output.dir="variants"

    def outname = 'pools_probands.gvcf'
    if (branch.num_samples) {
        outname = 'merge.' + branch.num_samples + '.pools_probands.gvcf'
    }

    from ('*.gvcf') produce (outname){

    exec """
            $GATK --java-options "-Xmx12g" CombineGVCFs
                -R $REF
                ${inputs.gvcf.withFlag("--variant ")}
                -O $output.gvcf
    """
    }
}

@transform('vcf')
joint_calling = {
  doc "Do joint calling on a multi-sample GVCF"

    output.dir="variants"

    exec """
        $GATK --java-options "-Xmx72g" GenotypeGVCFs
            -R $REF
            -V $input.gvcf
            -O $output.vcf
    ""","joint_calling"
}
