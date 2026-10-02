using UnrealBuildTool;

public class TerekDrive : ModuleRules
{
    public TerekDrive(ReadOnlyTargetRules Target) : base(Target)
    {
        PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;
        PublicDependencyModuleNames.AddRange(new [] {
            "Core","CoreUObject","Engine","InputCore","UMG","Slate","SlateCore"
        });
    }
}